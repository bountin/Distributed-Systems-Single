package ownModel;

public class FileServerInfo {
	FileServerId id;
	boolean online;
	int usage;
	long lastSeen = 0;

	public FileServerInfo(FileServerId id, boolean online, int usage) {
		this.id = id;
		this.online = online;
		this.usage = usage;
	}

	public FileServerId getId() {
		return id;
	}

	public boolean isOnline() {
		return online;
	}

	public int getUsage() {
		return usage;
	}

	public long getLastSeen() {
		return lastSeen;
	}

	public void bumpLastSeen() {
		lastSeen = System.currentTimeMillis();
	}

	public void setOffline() {
		online = false;
	}

	public void setOnline() {
		online = true;
	}
}
