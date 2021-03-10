package de.evoila.cf.broker.custom.mongodb;

import com.mongodb.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import de.evoila.cf.broker.bean.ExistingEndpointBean;
import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.model.Platform;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.model.credential.UsernamePasswordCredential;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author @author René Schollmeyer, Johannes Hiemer.
 */
@Service
public class MongoDBCustomImplementation {

    private Logger log = LoggerFactory.getLogger(MongoDBCustomImplementation.class);

    private ExistingEndpointBean existingEndpointBean;

    public MongoDBCustomImplementation(ExistingEndpointBean existingEndpointBean) {
        this.existingEndpointBean = existingEndpointBean;
    }

    public void createDatabase(MongoDBService connection, String database) throws PlatformException {
        try {
            MongoClient mongoClient = connection.mongoClient();
            MongoDatabase mongoDatabase = mongoClient.getDatabase(database);
            mongoDatabase.createCollection("_auth");
            MongoCollection collection = mongoDatabase.getCollection("_auth");
            collection.insertOne(new Document("auth", "auth"));
            collection.drop();
        } catch(MongoException e) {
            throw new PlatformException("Could not add to database", e);
        }
    }

    public void deleteDatabase(MongoDBService connection, String database) throws PlatformException {
        try {
            connection.mongoClient().getDatabase(database).drop();
        } catch (MongoException e) {
            throw new PlatformException("Could not remove from database", e);
        }
    }

    public static void createUserForDatabase(MongoDBService mongoDbService, String database, String username,
                                             String password) {
        createUserForDatabaseWithRoles(mongoDbService, database, username, password, "readWrite");
    }

    public static void createUserForDatabaseWithRoles(MongoDBService mongoDbService, String database, String username,
                                                      String password, String... roles) {
        Map<String, Object> commandArguments = new BasicDBObject();
        commandArguments.put("createUser", username);
        commandArguments.put("pwd", password);
        commandArguments.put("roles", roles);
        BasicDBObject command = new BasicDBObject(commandArguments);

        mongoDbService.mongoClient().getDatabase(database).runCommand(command);
    }

    public MongoDBService connection(ServiceInstance serviceInstance, Plan plan, UsernamePasswordCredential usernamePasswordCredential) {
        MongoDBService mongoDbService = new MongoDBService();

        if(plan.getPlatform() == Platform.BOSH)
            mongoDbService.createConnection(usernamePasswordCredential.getUsername(), usernamePasswordCredential.getPassword(),
                    "admin", serviceInstance.getHosts());
        else if (plan.getPlatform() == Platform.EXISTING_SERVICE)
            mongoDbService.createConnection(existingEndpointBean.getUsername(), existingEndpointBean.getPassword(),
                    existingEndpointBean.getDatabase(), existingEndpointBean.getHosts());

        return mongoDbService;
    }

    public static void close(MongoDBService mongoDbService){
        mongoDbService.close();
    }
    
}
