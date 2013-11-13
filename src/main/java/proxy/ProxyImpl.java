package proxy;

import message.Request;
import message.Response;
import message.request.*;
import message.response.BuyResponse;
import message.response.CreditsResponse;
import message.response.LoginResponse;
import message.response.MessageResponse;
import util.Config;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class ProxyImpl {

	static final HashMap<String, Integer> creditList   = new HashMap<String, Integer>();
	static HashMap<String, String>  passwordList = new HashMap<String, String>();

	public static void main(String[] args) throws IOException {
		Config config = new Config("proxy");

		int portTCP, portUDP, timeout, checkPeriod;

		try {
			portTCP = config.getInt("tcp.port");
			portUDP = config.getInt("udp.port");
			timeout = config.getInt("fileserver.timeout");
			checkPeriod = config.getInt("fileserver.checkPeriod");
		} catch (MissingResourceException e) {
			System.out.println("A configuration parameter is missing: " + e.getMessage());
			System.exit(1);
			return;
		}

		Config userConfig = new Config("user");
		Enumeration<String> users = ResourceBundle.getBundle("user").getKeys();

		while (users.hasMoreElements()) {
			String configLine = users.nextElement();
			String user = configLine.substring(0, configLine.indexOf('.'));
			String key = configLine.substring(configLine.indexOf('.') + 1);

			if (key.equals("credits") && !creditList.containsKey(user)) {
				creditList.put(user, userConfig.getInt(configLine));

			} else if (key.equals("password") && !passwordList.containsKey(user)) {
				passwordList.put(user, userConfig.getString(configLine));
			}
		}

		ServerSocket tcpSocket = new ServerSocket(portTCP);
		TCPThread.initNewThread(tcpSocket);
	}

	private static class TCPThread implements Runnable, IProxy {
		private ServerSocket tcpSocket;
		String user = null;

		private TCPThread(ServerSocket tcpSocket) {
			this.tcpSocket = tcpSocket;
		}

		static public void initNewThread(ServerSocket tcpSocket) {
			(new Thread(new TCPThread(tcpSocket))).start();
		}

		public void run() {
			Socket socket;
			try {
				socket = tcpSocket.accept();
				initNewThread(tcpSocket);

				ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
				out.flush();
				ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

				Object request;

				while (true) {
					request = in.readObject();

					Response response;

					try {
						if (request instanceof LoginRequest) {
							response = login((LoginRequest) request);
						} else if (request instanceof CreditsRequest) {
							response = credits();
						} else if (request instanceof BuyRequest) {
							response = buy((BuyRequest) request);
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
			} catch (EOFException unused) {
				// Client aborted connection
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
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

//	private static class UDPThread implements Runnable {
//
//		private DatagramSocket udpSocket;
//
//		private UDPThread(DatagramSocket udpSocket) {
//			this.udpSocket = udpSocket;
//		}
//
//		static public void initNewThread(DatagramSocket udpSocket) {
//			(new Thread(new UDPThread(udpSocket))).run();
//		}
//
//		public void run() {
//			Socket socket = null;
//			try {
//				socket = udpSocket.receive();
//
//				PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
//				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//
//				System.out.println(in.readLine());
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//		}
//	}
}
