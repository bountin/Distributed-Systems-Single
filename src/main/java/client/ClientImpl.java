package client;

import cli.Command;
import cli.Shell;
import cli.ShellThread;
import message.Request;
import message.Response;
import message.request.*;
import message.response.DownloadFileResponse;
import message.response.DownloadTicketResponse;
import message.response.LoginResponse;
import message.response.MessageResponse;
import model.DownloadTicket;
import ownModel.FileServerInfo;
import util.Config;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.MissingResourceException;
import java.util.Scanner;

public class ClientImpl {
	private Shell shell;
	private Config config;
	private ClientCommands commands;

	private String host;
	private String downloadDir;
	private int port;

	private ObjectOutputStream out;
	private ObjectInputStream in;

	private boolean loggedIn = false;

	public ClientImpl(Shell shell, Config config) {
		this.shell = shell;
		this.config = config;
	}

	public static void main(String[] args) throws IOException {
		Config config = new Config("client");
		Shell shell = new Shell("Client", System.out, System.in);

		ClientImpl client = new ClientImpl(shell, config);
		client.start();
	}

	public IClientCli start() throws IOException {
		try {
			downloadDir = config.getString("download.dir");
			host = config.getString("proxy.host");
			port = config.getInt("proxy.tcp.port");
		} catch (MissingResourceException e) {
			shell.writeLine("A configuration parameter is missing: " + e.getMessage());
			stop();
		}

		Socket socket = new Socket((String) null, port);
		out = new ObjectOutputStream(socket.getOutputStream());
		out.flush();
		in = new ObjectInputStream(socket.getInputStream());

		commands = new ClientCommands();
		shell.register(commands);
		ShellThread.initNewThread(shell);
		return commands;
	}

	private void stop() throws IOException {
		shell.close();
		System.in.close();
	}

	class ClientCommands implements IClientCli {
		@Override
		@Command
		public LoginResponse login(String username, String password) throws IOException {
			sendRequest(new LoginRequest(username, password));

			Response response = recvResponse();

			if (response instanceof LoginResponse) {
				LoginResponse loginResponse = (LoginResponse) response;
				if (loginResponse.getType() == LoginResponse.Type.SUCCESS) {
					loggedIn = true;
				}
				return loginResponse;
			} else {
				return null;
			}
		}

		@Override
		@Command
		public Response credits() throws IOException {
			sendRequest(new CreditsRequest());
			return recvResponse();
		}

		@Override
		@Command
		public Response buy(long credits) throws IOException {
			sendRequest(new BuyRequest(credits));
			return recvResponse();
		}

		@Override
		@Command
		public Response list() throws IOException {
			sendRequest(new ListRequest());
			return recvResponse();
		}

		@Override
		@Command
		public Response download(String filename) throws IOException {
			sendRequest(new DownloadTicketRequest(filename));
			Response response = recvResponse();
			if (response instanceof MessageResponse) return response;
			if (!(response instanceof DownloadTicketResponse)) return null;

			DownloadTicket ticket = ((DownloadTicketResponse) response).getTicket();
			response = fileServerRequest(new DownloadFileRequest(ticket), ticket.getAddress(), ticket.getPort());
			if (response instanceof MessageResponse) return response;
			if (!(response instanceof DownloadFileResponse)) return null;
			byte[] data = ((DownloadFileResponse) response).getContent();

			BufferedWriter writer = new BufferedWriter(new FileWriter(downloadDir + "/" + filename));
			writer.write(new String(data));
			writer.close();
			return response;
		}

		@Override
		@Command
		public MessageResponse upload(String filename) throws IOException {
			String text = new Scanner(new File(downloadDir + "/" + filename)).useDelimiter("\\A").next();
			UploadRequest request = new UploadRequest(filename, 0, text.getBytes());
			sendRequest(request);

			Response response = recvResponse();
			if (response instanceof MessageResponse) {
				return (MessageResponse) response;
			} else {
				return null;
			}
		}

		@Override
		@Command
		public MessageResponse logout() throws IOException {
			sendRequest(new LogoutRequest());

			Response response = recvResponse();
			if (response instanceof MessageResponse) {
				return (MessageResponse) response;
			} else {
				return null;
			}
		}

		@Command
		public MessageResponse exit() throws IOException {
			MessageResponse response = null;
			if (loggedIn)
				response = logout();

			stop();
			return response;
		}

		private void sendRequest(Request request) throws IOException {
			try {
				out.writeObject(request);
				out.flush();
			} catch (SocketException e) {
				shell.writeLine("Lost connection to Proxy");
				stop();
			}
		}

		private Response recvResponse() throws IOException {
			try {
				Object object = in.readObject();
				if (! (object instanceof Response)) {
					return new MessageResponse("Got an invalid response from the Proxy: " + object.getClass());
				}

				return (Response) object;
			} catch (SocketException e) {
				shell.writeLine("Lost connection to Proxy");
				stop();
				return null;
			} catch (EOFException e) {
				shell.writeLine("Lost connection to Proxy");
				stop();
				return null;
			} catch (ClassNotFoundException e) {
				return new MessageResponse("Got an invalid response from the Proxy: " + e.getMessage());
			}
		}

		/**
		 * @todo generalize this with proxy/ClientThred
		 */
		private Response fileServerRequest(Request request, InetAddress host, int port) throws IOException {
			Socket socket = new Socket(host, port);
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
			}

			if (!(response instanceof Response)) {
				return new MessageResponse("Unknown object: " + response.getClass());
			}

			return (Response) response;
		}

	}
}