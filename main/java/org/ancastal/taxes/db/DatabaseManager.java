package org.ancastal.taxes.db;

import java.nio.ByteBuffer;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DatabaseManager {
	private Connection connection;
	private String path;

	public DatabaseManager(String databasePath) throws SQLException {
		this.path = databasePath;
		connection = getConnection();
		System.out.println("Connected to database.");
	}

	public Connection getConnection() throws SQLException {
		if (connection == null || connection.isClosed()) {
			connection = DriverManager.getConnection("jdbc:sqlite:" + this.path);
		}
		return connection;
	}

	public void initializeDatabase() throws SQLException {
		try (Statement statement = getConnection().createStatement()) {
			// Create the table
			String createTableSql = "CREATE TABLE IF NOT EXISTS taxes (uuid BINARY(16) PRIMARY KEY, username TEXT, balance DOUBLE)";
			statement.executeUpdate(createTableSql);

		}
	}

	public void close() throws SQLException {
		connection.close();
	}

	public void truncateTable() throws SQLException {
		try (Connection connection = getConnection();
			 Statement statement = connection.createStatement()) {

			String query = "DELETE FROM taxes";
			statement.executeUpdate(query);
		}
	}


	public void addPlayer(UUID uuid, String username, double balance) throws SQLException {
		try (PreparedStatement selectStatement = getConnection().prepareStatement(
				"SELECT uuid FROM taxes WHERE uuid = ?")) {
			selectStatement.setBytes(1, getBytesFromUUID(uuid));
			try (ResultSet resultSet = selectStatement.executeQuery()) {
				if (resultSet.next()) {
					// UUID already exists in the table
					return;
				}
			}
		}

		try (PreparedStatement insertStatement = getConnection().prepareStatement(
				"INSERT INTO taxes (uuid, username, balance) VALUES (?, ?, ?)")) {
			insertStatement.setBytes(1, getBytesFromUUID(uuid));
			insertStatement.setString(2, username);
			insertStatement.setDouble(3, balance);
			insertStatement.executeUpdate();
		}
	}

	public void addPlayerOpen(Connection connection, UUID uuid, String username, double balance) throws SQLException {
		try (PreparedStatement selectStatement = connection.prepareStatement(
				"SELECT uuid FROM taxes WHERE uuid = ?")) {
			selectStatement.setBytes(1, getBytesFromUUID(uuid));
			try (ResultSet resultSet = selectStatement.executeQuery()) {
				if (resultSet.next()) {
					// UUID already exists in the table
					return;
				}
			}
		}

		try (PreparedStatement insertStatement = connection.prepareStatement(
				"INSERT INTO taxes (uuid, username, balance) VALUES (?, ?, ?)")) {
			insertStatement.setBytes(1, getBytesFromUUID(uuid));
			insertStatement.setString(2, username);
			insertStatement.setDouble(3, balance);
			insertStatement.executeUpdate();
		}
	}

	public Map<UUID, Double> getPlayers() throws SQLException {
		Map<UUID, Double> playerBalances = new HashMap<>();
		try (PreparedStatement statement = getConnection().prepareStatement(
				"SELECT uuid, balance FROM taxes")) {
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					UUID uuid = getUUIDFromBytes(resultSet.getBytes("uuid"));
					double balance = resultSet.getDouble("balance");
					playerBalances.put(uuid, balance);
				}
			}
		}
		return playerBalances;
	}

	private byte[] getBytesFromUUID(UUID uuid) {
		byte[] bytes = new byte[16];
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		buffer.putLong(uuid.getMostSignificantBits());
		buffer.putLong(uuid.getLeastSignificantBits());
		return bytes;
	}

	private UUID getUUIDFromBytes(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		long high = buffer.getLong();
		long low = buffer.getLong();
		return new UUID(high, low);
	}


}
