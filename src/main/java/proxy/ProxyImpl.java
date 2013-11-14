package proxy;

import cli.Command;
import cli.Shell;
import message.Response;
import message.response.MessageResponse;
import ownModel.FileServerId;
import ownModel.FileServerInfo;
import util.Config;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.*;

public class ProxyImpl {

	static final HashMap<String, Integer> creditList   = new HashMap<>();
	static final HashMap<String, String>  passwordList = new HashMap<>();
	static final HashMap<String, ArrayList<ClientThread>> onlineList = new HashMap<>();
	static final ArrayList<ObjectOutputStream> streamList = new ArrayList<>();
	static final HashMap<FileServerId, FileServerInfo> fileServerList = new HashMap<>();

	private static Shell shell;
	private static ServerSocket tcpSocket;
	private static DatagramSocket udpSocket;

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

		tcpSocket = new ServerSocket(portTCP);
		ClientThread.initNewThread(tcpSocket, creditList, passwordList, onlineList, streamList);

		udpSocket = new DatagramSocket(portUDP);
		AliveReceivingTask.init(udpSocket, checkPeriod, fileServerList);

		shell = new Shell("Client", System.out, System.in);
		shell.register(new ProxyCommands());
		shell.run();
	}

	private static class ProxyCommands implements IProxyCli {
		@Override
		@Command
		public Response fileservers() throws IOException {
			return null;  //To change body of implemented methods use File | Settings | File Templates.
		}

		@Override
		@Command
		public Response users() throws IOException {
			StringBuilder message = new StringBuilder();

			Set<String> users = creditList.keySet();

			int i = 1;
			for (String user: users) {
				boolean online = false;
				if (onlineList.containsKey(user) && onlineList.get(user).size() >= 1) {
					online = true;
				}

				message.append(i++ + ". " + user + " " + (online ? "online" : "offline") + " Credits: " + creditList.get(user) + '\n');
			}

			return new MessageResponse(message.toString());
		}

		@Override
		@Command
		public MessageResponse exit() throws IOException {
			for (ObjectOutputStream stream: streamList) {
				stream.close();
			}

			tcpSocket.close();
			shell.close();
			System.in.close();
			return new MessageResponse("Proxy stopping");
		}
	}
}
