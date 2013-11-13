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

			Object response;
			try {
				response = in.readObject();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
				return null;
			}

			if (response instanceof LoginResponse) {
				return (LoginResponse) response;
			} else {
				return null;
			}
		}

		@Override
		@Command
		public Response credits() throws IOException {
			sendRequest(new CreditsRequest());

			Object response;
			try {
				response = in.readObject();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
				return null;
			}

			if (response instanceof Response) {
				return (Response) response;
			}

			return null;
		}

		@Override
		@Command
		public Response buy(long credits) throws IOException {
			sendRequest(new BuyRequest(credits));

			Object response;
			try {
				response = in.readObject();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
				return null;
			}

			if (response instanceof Response) {
				return (Response) response;
			}

			return null;
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
			return null;
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
	}
}