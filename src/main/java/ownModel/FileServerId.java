package ownModel;

public class FileServerId {

	private String host;
	private int port;

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public FileServerId(String host, int port) {
		this.host = host;
		this.port = port;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		FileServerId that = (FileServerId) o;

		if (port != that.port) return false;
		if (!host.equals(that.host)) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result = host.hashCode();
		result = 31 * result + port;
		return result;
	}
}
