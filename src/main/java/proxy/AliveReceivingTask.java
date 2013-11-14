package proxy;

import ownModel.FileServerId;
import ownModel.FileServerInfo;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class AliveReceivingTask extends TimerTask {
	private final DatagramSocket socket;
	private final int checkPeriod;
	private final HashMap<FileServerId, ownModel.FileServerInfo> fileServerList;

	private final Timer timer;

	public AliveReceivingTask(DatagramSocket socket, int checkPeriod, HashMap<FileServerId, ownModel.FileServerInfo> fileServerList, Timer timer) {
		this.socket = socket;
		this.checkPeriod = checkPeriod;
		this.fileServerList = fileServerList;
		this.timer = timer;
	}

	public static AliveReceivingTask init(DatagramSocket socket, int checkPeriod, HashMap<FileServerId, ownModel.FileServerInfo> fileServerList) {
		Timer timer = new Timer("AliveReceiver");
		AliveReceivingTask task = new AliveReceivingTask(socket, checkPeriod, fileServerList, timer);
		timer.schedule(task, 0, checkPeriod);
		return task;
	}

	@Override
	public void run() {
		try {
			byte[] buf = new byte[4];
			DatagramPacket data = new DatagramPacket(buf, 4);

			socket.setSoTimeout(checkPeriod);

			while (true) {
				socket.receive(data);
				handleMessage(data, buf);
			}
		} catch (SocketException ignored) {
			// Dying
		} catch (SocketTimeoutException e) {
			// Socket time out
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void handleMessage(DatagramPacket packet, byte[] data) {
		int port = ByteBuffer.wrap(data).asIntBuffer().get();
		String host = packet.getAddress().getHostAddress();

		FileServerId id = new FileServerId(host, port);

		synchronized (fileServerList) {
			if (fileServerList.containsKey(id)) {
				fileServerList.get(id).bumpLastSeen();
			} else {
				FileServerInfo info = new FileServerInfo(id, true, 0);
				fileServerList.put(id, info);
			}
		}
	}

	public void stop() {
		timer.cancel();
	}
}
