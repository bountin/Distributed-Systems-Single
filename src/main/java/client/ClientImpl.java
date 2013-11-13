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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.MissingResourceException;

public class ClientImpl {
	private static Shell shell;
	private static String host;
	private static String downloadDir;
	private static int port;

	private static ObjectOutputStream out;
	private static ObjectInputStream in;

	private static boolean loggedIn = false;

	public static void main(String[] args) throws IOException {
		Config config = new Config("client");

		try {
			downloadDir = config.getString("download.dir");
			host = config.getString("proxy.host");
			port = config.getInt("proxy.tcp.port");
		} catch (MissingResourceException e) {
			System.out.println("A configuration parameter is missing: " + e.getMessage());
			System.exit(1);
			return;
		}

		Socket socket = new Socket((String) null, port);
		out = new ObjectOutputStream(socket.getOutputStream());
		out.flush();
		in = new ObjectInputStream(socket.getInputStream());

		shell = new Shell("Client", System.out, System.in);
		shell.register(new ClientCommands());
		shell.run();
	}

	static class ClientCommands implements IClientCli {
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
			shell.close();
			System.in.close();
			return null;
		}

		private void sendRequest(Request request) throws IOException {
			out.writeObject(request);
			out.flush();
		}

		private Response recvResponse() throws IOException {
			try {
				Object object = in.readObject();
				if (! (object instanceof Response)) {
					return new MessageResponse("Got an invalid response from the Proxy: " + object.getClass());
				}

				return (Response) object;
			} catch (ClassNotFoundException e) {
				return new MessageResponse("Got an invalid response from the Proxy: " + e.getMessage());
			}
		}
	}
}