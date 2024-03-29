package proxy;

import message.Request;
import message.Response;
import message.request.*;
import message.response.*;
import model.DownloadTicket;
import ownModel.FileServerId;
import ownModel.FileServerInfo;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import static util.ChecksumUtils.generateChecksum;

class ClientThread implements Runnable, IProxy {
	private ServerSocket tcpSocket;
	private final HashMap<String, ArrayList<ClientThread>> onlineList;
	private final HashMap<String, Integer> creditList;
	private final HashMap<String, String> passwordList;
	private final ArrayList<ObjectOutputStream> streamList;
	private final HashMap<FileServerId, FileServerInfo> fileServerList;
	String user = null;

	private ClientThread(ServerSocket tcpSocket, HashMap<String, Integer> creditList, HashMap<String, String> passwordList, HashMap<String, ArrayList<ClientThread>> onlineList, ArrayList<ObjectOutputStream> streamList, HashMap<FileServerId, FileServerInfo> fileServerList) {
		this.tcpSocket = tcpSocket;
		this.creditList = creditList;
		this.passwordList = passwordList;
		this.onlineList = onlineList;
		this.streamList = streamList;
		this.fileServerList = fileServerList;
	}

	static public void initNewThread(ServerSocket tcpSocket, HashMap<String, Integer> creditList, HashMap<String, String> passwordList, HashMap<String, ArrayList<ClientThread>> onlineList, ArrayList<ObjectOutputStream> streamList, HashMap<FileServerId, FileServerInfo> fileServerList) {
		(new Thread(new ClientThread(tcpSocket, creditList, passwordList, onlineList, streamList, fileServerList))).start();
	}

	public void run() {
		Socket socket;
		ObjectOutputStream out = null;
		ObjectInputStream in = null;
		try {
			socket = tcpSocket.accept();
			initNewThread(tcpSocket, creditList, passwordList, onlineList, streamList, fileServerList);

			out = new ObjectOutputStream(socket.getOutputStream());
			synchronized (streamList) {
				streamList.add(out);
			}
			out.flush();
			in = new ObjectInputStream(socket.getInputStream());

			while (true) {
				handleRequestLoop(out, in);
			}
		} catch (EOFException ignored) {
			// Client aborted connection
		} catch (SocketException ignored) {
			// Server is shutting down
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} finally {
			if (user != null) {
				synchronized (onlineList) {
					onlineList.get(user).remove(this);
				}
			}
			if (in != null) {
				try {
					in.close();
				} catch (IOException ignored) {}
			}
			if (out != null) {
				try {
					out.close();
				} catch (IOException ignored) {}
			}
		}
	}

	private void handleRequestLoop(ObjectOutputStream out, ObjectInputStream in) throws IOException, ClassNotFoundException {
		Object request = in.readObject();
		Response response;

		try {
			if (request instanceof LoginRequest) {
				response = login((LoginRequest) request);
			} else if (request instanceof CreditsRequest) {
				response = credits();
			} else if (request instanceof BuyRequest) {
				response = buy((BuyRequest) request);
			} else if (request instanceof LogoutRequest) {
				response = logout();
			} else if (request instanceof DownloadTicketRequest) {
				response = download((DownloadTicketRequest) request);
			} else if (request instanceof ListRequest) {
				response = list();
			} else if (request instanceof UploadRequest) {
				response = upload((UploadRequest) request);
			} else if (request instanceof Request) {
				response = new MessageResponse("Unsupported Request: " + request.getClass());
			} else {
				response = new MessageResponse("Got a non-request object");
			}
		} catch (NotLoggedInException unused) {
			response = new MessageResponse("Please login first");
		}

		out.writeObject(response);
		out.flush();
	}

	@Override
	public LoginResponse login(LoginRequest request) throws IOException {
		// First check if the client is already logged in and log him out
		if (user != null) {
			this.logout();
		}

		// Check if user is known - The type enum does not support this so just write WRONG_CREDENTIALS
		synchronized (passwordList) {
			if (! passwordList.containsKey(request.getUsername())) {
				return new LoginResponse(LoginResponse.Type.WRONG_CREDENTIALS);
			}
		}

		synchronized (passwordList) {
			// Check if passwort is correct
			if (! passwordList.get(request.getUsername()).equals(request.getPassword())) {
				return new LoginResponse(LoginResponse.Type.WRONG_CREDENTIALS);
			}
		}

		user = request.getUsername();
		synchronized (onlineList) {
			if (! onlineList.containsKey(user)) {
				onlineList.put(user, new ArrayList<ClientThread>());
			}

			onlineList.get(user).add(this);
		}

		return new LoginResponse(LoginResponse.Type.SUCCESS);
	}

	@Override
	public Response credits() throws IOException {
		checkLoginStatus();

		int credits;
		synchronized (creditList) {
			credits = creditList.get(user);
		}
		return new CreditsResponse(credits);
	}

	@Override
	public Response buy(BuyRequest credits) throws IOException {
		checkLoginStatus();

		int userCredits = incUserCredits(credits.getCredits());
		return new BuyResponse(userCredits);
	}

	private int incUserCredits(long credits) {
		int userCredits;
		long creditsToBeBought = Math.max(0, credits);
		synchronized (creditList) {
			userCredits = creditList.get(user);
			userCredits += creditsToBeBought;
			creditList.put(user, userCredits);
		}
		return userCredits;
	}

	@Override
	public Response list() throws IOException {
		checkLoginStatus();
		return fileServerRequest(new ListRequest());
	}

	@Override
	public Response download(DownloadTicketRequest request) throws IOException {
		checkLoginStatus();
		Response response = fileServerRequest(new InfoRequest(request.getFilename()));
		if (response instanceof MessageResponse) {
			return response;
		} else if (!(response instanceof InfoResponse)) {
			return new MessageResponse("Got invalid answer from File Server: " + response.toString());
		}

		// Choose a FS
		FileServerInfo serverInfo;
		try {
			serverInfo = chooseFileServer();
		} catch (NoFileServerPresentException e) {
			return new MessageResponse("No FileServer available");
		}

		// Validate credits
		InfoResponse info = (InfoResponse) response;
		Integer credits;
		synchronized (creditList) {
			credits = creditList.get(user);

			if (info.getSize() > credits) {
				return new MessageResponse("User " + user + " does not have enough credits left\nNeeded: " + info.getSize() + "\nAvailable: "+credits);
			}

			creditList.put(user, credits - (int)info.getSize());
		}

		// Increase FS's usage
		serverInfo.incUsage(info.getSize());

		String checksum = generateChecksum(user, info.getFilename(), 1, info.getSize());
		DownloadTicket ticket = new DownloadTicket(user, info.getFilename(), checksum, InetAddress.getByName(serverInfo.getId().getHost()), serverInfo.getId().getPort());
		return new DownloadTicketResponse(ticket);
	}

	@Override
	public MessageResponse upload(UploadRequest request) throws IOException {
		checkLoginStatus();
		fileServerBroadcast(request);
		int credits = incUserCredits(request.getContent().length * 2);
		return new MessageResponse("File successfully uploaded.\nYou now habe " + credits + " Credits.");
	}

	@Override
	public MessageResponse logout() throws IOException {
		MessageResponse response = new MessageResponse("Successfully logged out. User was " + user);
		synchronized (onlineList) {
			onlineList.get(user).remove(this);
		}
		user = null;
		return response;
	}

	private void fileServerBroadcast(Request request) throws IOException {
		Collection<FileServerInfo> servers;
		synchronized (fileServerList) {
			servers = fileServerList.values();
		}
		for (FileServerInfo info: servers) {
			if (info.isOnline()) {
				fileServerRequest(request, info);
			}
		}
	}

	private Response fileServerRequest(Request request) throws IOException {
		FileServerInfo serverInfo;
		try {
			serverInfo = chooseFileServer();
		} catch (NoFileServerPresentException e) {
			return new MessageResponse("Unable to execute command: No FileServer is present");
		}

		return fileServerRequest(request, serverInfo);
	}

	/**
	 * @todo generalize this with ClientImpl
	 */
	private Response fileServerRequest(Request request, FileServerInfo serverInfo) throws IOException {
		Socket socket = new Socket(serverInfo.getId().getHost(), serverInfo.getId().getPort());
		ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
		out.flush();
		ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

		out.writeObject(request);
		out.flush();

		Object response;
		try {
			response = in.readObject();
		} catch (ClassNotFoundException e) {
			return new MessageResponse("Unknown class: " + e.getMessage());
		} finally {
			out.close();
			in.close();
			socket.close();
		}

		if (!(response instanceof Response)) {
			return new MessageResponse("Unknown object: " + response.getClass());
		}

		return (Response) response;
	}

	private FileServerInfo chooseFileServer() throws NoFileServerPresentException {
		Collection<FileServerInfo> servers;
		synchronized (fileServerList) {
			servers = fileServerList.values();
		}

		// Look for the server with the smallest usage
		FileServerInfo minUsageServer = null;
		for (FileServerInfo info: servers) {
			if (!info.isOnline()) continue;

			if (minUsageServer == null || minUsageServer.getUsage() > info.getUsage()) {
				minUsageServer = info;
			}
		}

		if (minUsageServer == null) throw new NoFileServerPresentException();

		return minUsageServer;
	}

	private void checkLoginStatus() throws NotLoggedInException {
		if (user == null) {
			throw new NotLoggedInException();
		}
	}

	private class NotLoggedInException extends IOException {}

	private class NoFileServerPresentException extends Throwable {}
}
