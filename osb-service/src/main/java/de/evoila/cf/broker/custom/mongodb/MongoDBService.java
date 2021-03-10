/**
 * 
 */
package de.evoila.cf.broker.custom.mongodb;

import com.mongodb.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Johannes Hiemer
 *
 */
public class MongoDBService {

	private MongoClient mongoClient;

	public boolean isConnected() {
		return mongoClient != null && mongoClient.listDatabases() != null;
	}

    public MongoClient mongoClient() {
        return mongoClient;
    }

	public void createConnection(String username, String password, String database, List<de.evoila.cf.broker.model.catalog.ServerAddress> hosts) {
		if(database == null)
			database = "admin";
		
		List<ServerAddress> serverAddresses = new ArrayList<>();
		for (de.evoila.cf.broker.model.catalog.ServerAddress host : hosts) {
			serverAddresses.add(new ServerAddress(host.getIp(), host.getPort()));
		}

		MongoCredential mongoCredential = MongoCredential.createScramSha1Credential(username, database, password.toCharArray());
		mongoClient = MongoClients.create(MongoClientSettings.builder()
				.applyToClusterSettings(builder -> 
						builder.hosts(serverAddresses))
				.credential(mongoCredential)
				.build());
	}
	
	public void close(){
		mongoClient.close();
	}

}
