package proxy;

import ownModel.FileServerId;
import ownModel.FileServerInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class AliveGarbageCollectionTask extends TimerTask {
	private final Timer timer;
	private final HashMap<FileServerId, FileServerInfo> fileServerList;
	private final int timeout;

	public AliveGarbageCollectionTask(Timer timer, HashMap<FileServerId, FileServerInfo> fileServerList, int timeout) {
		this.timer = timer;
		this.fileServerList = fileServerList;
		this.timeout = timeout;
	}

	@Override
	public void run() {
		Collection<FileServerInfo> serverInfos;
		synchronized (fileServerList) {
			serverInfos = fileServerList.values();
		}

		for (FileServerInfo info: serverInfos) {
			long gateTimeStamp = System.currentTimeMillis() - timeout;
			if (info.getLastSeen() < gateTimeStamp) {
				info.setOffline();
			} else {
				info.setOnline();
			}
		}
	}

	public static AliveGarbageCollectionTask init(int checkPeriod, HashMap<FileServerId, FileServerInfo> fileServerList, int timeout) {
		Timer timer = new Timer("AliveGC");
		AliveGarbageCollectionTask task = new AliveGarbageCollectionTask(timer, fileServerList, timeout);
		timer.schedule(task, 0, checkPeriod);
		return task;
	}

	public void stop() {
		timer.cancel();
	}
}
