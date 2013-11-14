package client;

import cli.Command;
import cli.Shell;
import message.Request;
import message.Response;
import message.request.BuyRequest;
import message.request.CreditsRequest;
import message.request.LoginRequest;
import message.request.LogoutRequest;
import message.response.LoginResponse;
import message.response.MessageResponse;
import util.Config;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.MissingResourceException;

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
		shell.run();
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
			return null;
		}

		@Override
		@Command
		public Response download(String filename) throws IOException {
			return null;
		}

		@Override
		@Command
		public MessageResponse upload(String filename) throws IOException {
			return null;
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
			} catch (EOFException e) {
				shell.writeLine("Lost connection to Proxy");
				stop();
				return null;
			} catch (ClassNotFoundException e) {
				return new MessageResponse("Got an invalid response from the Proxy: " + e.getMessage());
			}
		}
	}
}