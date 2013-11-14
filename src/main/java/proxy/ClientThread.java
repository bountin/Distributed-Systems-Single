package proxy;

import message.Request;
import message.Response;
import message.request.*;
import message.response.BuyResponse;
import message.response.CreditsResponse;
import message.response.LoginResponse;
import message.response.MessageResponse;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;

class ClientThread implements Runnable, IProxy {
	private ServerSocket tcpSocket;
	private final HashMap<String, ArrayList<ClientThread>> onlineList;
	private final HashMap<String, Integer> creditList;
	private final HashMap<String, String> passwordList;
	private final ArrayList<ObjectOutputStream> streamList;
	String user = null;

	private ClientThread(ServerSocket tcpSocket, HashMap<String, Integer> creditList, HashMap<String, String> passwordList, HashMap<String, ArrayList<ClientThread>> onlineList, ArrayList<ObjectOutputStream> streamList) {
		this.tcpSocket = tcpSocket;
		this.creditList = creditList;
		this.passwordList = passwordList;
		this.onlineList = onlineList;
		this.streamList = streamList;
	}

	static public void initNewThread(ServerSocket tcpSocket, HashMap<String, Integer> creditList, HashMap<String, String> passwordList, HashMap<String, ArrayList<ClientThread>> onlineList, ArrayList<ObjectOutputStream> streamList) {
		(new Thread(new ClientThread(tcpSocket, creditList, passwordList, onlineList, streamList))).start();
	}

	public void run() {
		Socket socket;
		ObjectOutputStream out = null;
		ObjectInputStream in = null;
		try {
			socket = tcpSocket.accept();
			initNewThread(tcpSocket, creditList, passwordList, onlineList, streamList);

			out = new ObjectOutputStream(socket.getOutputStream());
			streamList.add(out);
			out.flush();
			in = new ObjectInputStream(socket.getInputStream());

			while (true) {
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

	@Override
	public LoginResponse login(LoginRequest request) throws IOException {
		// Check if user is known - The type enum does not support this so just write WRONG_CREDENTIALS
		if (! passwordList.containsKey(request.getUsername())) {
			return new LoginResponse(LoginResponse.Type.WRONG_CREDENTIALS);
		}

		// Check if passwort is correct
		if (! passwordList.get(request.getUsername()).equals(request.getPassword())) {
			return new LoginResponse(LoginResponse.Type.WRONG_CREDENTIALS);
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

		int userCredits;
		long creditsToBeBought = Math.max(0, credits.getCredits());
		synchronized (creditList) {
			userCredits = creditList.get(user);
			userCredits += creditsToBeBought;
			creditList.put(user, userCredits);
		}
		return new BuyResponse(userCredits);
	}

	@Override
	public Response list() throws IOException {
		return null;  //To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public Response download(DownloadTicketRequest request) throws IOException {
		return null;  //To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public MessageResponse upload(UploadRequest request) throws IOException {
		return null;  //To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public MessageResponse logout() throws IOException {
		MessageResponse response = new MessageResponse("Logged out " + user);
		user = null;
		return response;
	}

	private void checkLoginStatus() throws NotLoggedInException {
		if (user == null) {
			throw new NotLoggedInException();
		}
	}

	private class NotLoggedInException extends IOException {
	}
}
