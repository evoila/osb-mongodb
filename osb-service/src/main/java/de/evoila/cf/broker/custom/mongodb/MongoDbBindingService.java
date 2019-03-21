package de.evoila.cf.broker.custom.mongodb;

import com.mongodb.BasicDBObject;
import de.evoila.cf.broker.model.RouteBinding;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.ServiceInstanceBinding;
import de.evoila.cf.broker.model.ServiceInstanceBindingRequest;
import de.evoila.cf.broker.model.catalog.ServerAddress;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.model.credential.UsernamePasswordCredential;
import de.evoila.cf.broker.repository.*;
import de.evoila.cf.broker.service.AsyncBindingService;
import de.evoila.cf.broker.service.HAProxyService;
import de.evoila.cf.broker.service.impl.BindingServiceImpl;
import de.evoila.cf.broker.util.ServiceInstanceUtils;
import de.evoila.cf.cpi.CredentialConstants;
import de.evoila.cf.security.credentials.CredentialStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Johannes Hiemer.
 */
@Service
public class MongoDbBindingService extends BindingServiceImpl {

    private Logger log = LoggerFactory.getLogger(MongoDbBindingService.class);

    private static String URI = "uri";
    private static String USERNAME = "user";
    private static String PASSWORD = "password";
    private static String DATABASE = "database";

    private MongoDBCustomImplementation mongoDBCustomImplementation;

    private CredentialStore credentialStore;

    public MongoDbBindingService(BindingRepository bindingRepository, ServiceDefinitionRepository serviceDefinitionRepository,
                                 ServiceInstanceRepository serviceInstanceRepository, RouteBindingRepository routeBindingRepository,
                                 @Autowired(required = false) HAProxyService haProxyService, MongoDBCustomImplementation mongoDBCustomImplementation,
                                 JobRepository jobRepository, AsyncBindingService asyncBindingService,
                                 PlatformRepository platformRepository, CredentialStore credentialStore) {
        super(bindingRepository, serviceDefinitionRepository, serviceInstanceRepository, routeBindingRepository,
                haProxyService, jobRepository, asyncBindingService, platformRepository);
        this.credentialStore = credentialStore;
        this.mongoDBCustomImplementation = mongoDBCustomImplementation;
    }

    @Override
    protected Map<String, Object> createCredentials(String bindingId, ServiceInstanceBindingRequest serviceInstanceBindingRequest,
                                                    ServiceInstance serviceInstance, Plan plan, ServerAddress host) {
        UsernamePasswordCredential usernamePasswordCredential = credentialStore.getUser(serviceInstance, CredentialConstants.ROOT_CREDENTIALS);

        MongoDbService mongoDbService = mongoDBCustomImplementation.connection(serviceInstance, plan, usernamePasswordCredential);

        credentialStore.createUser(serviceInstance, bindingId);
        UsernamePasswordCredential bindingCredentials = credentialStore.getUser(serviceInstance, bindingId);

        String username = bindingCredentials.getUsername();
        String password = bindingCredentials.getPassword();
        String database = serviceInstance.getId();

        MongoDBCustomImplementation.createUserForDatabase(mongoDbService, database, username, password);

        List<ServerAddress> mongodbHosts = serviceInstance.getHosts();
        String ingressInstanceGroup = plan.getMetadata().getIngressInstanceGroup();
        if (ingressInstanceGroup != null && ingressInstanceGroup.length() > 0) {
            mongodbHosts = ServiceInstanceUtils.filteredServerAddress(serviceInstance.getHosts(),ingressInstanceGroup);
        }

        String endpoint = ServiceInstanceUtils.connectionUrl(mongodbHosts);

        // When host is not empty, it is a service key
        if (host != null)
            endpoint = host.getIp() + ":" + host.getPort();

        String dbURL = String.format("mongodb://%s:%s@%s/%s", username, password, endpoint, database);

        String replicaSet = (String) serviceInstance.getParameters().get("replicaSet");

        if (replicaSet != null && !replicaSet.equals(""))
            dbURL += String.format("?replicaSet=%s", replicaSet);

        Map<String, Object> credentials = new HashMap<String, Object>();
        credentials.put(URI, dbURL);
        credentials.put(USERNAME, username);
        credentials.put(PASSWORD, password);
        credentials.put(DATABASE, database);

        return credentials;
    }

    @Override
    protected void unbindService(ServiceInstanceBinding binding, ServiceInstance serviceInstance, Plan plan) {
        UsernamePasswordCredential usernamePasswordCredential = credentialStore.getUser(serviceInstance, CredentialConstants.ROOT_CREDENTIALS);
        MongoDbService mongoDbService = mongoDBCustomImplementation.connection(serviceInstance, plan, usernamePasswordCredential);

        UsernamePasswordCredential bindingCredentials = credentialStore.getUser(serviceInstance, binding.getId());

        mongoDbService.mongoClient().getDatabase(binding.getCredentials().get(DATABASE).toString())
                .runCommand(new BasicDBObject("dropUser", bindingCredentials.getUsername()));

        credentialStore.deleteCredentials(serviceInstance, binding.getId());
    }

    @Override
    protected ServiceInstanceBinding bindServiceKey(String bindingId, ServiceInstanceBindingRequest serviceInstanceBindingRequest,
                                                    ServiceInstance serviceInstance, Plan plan,
                                                    List<ServerAddress> externalAddresses) {

        Map<String, Object> credentials = createCredentials(bindingId, null, serviceInstance, plan, externalAddresses.get(0));

        ServiceInstanceBinding serviceInstanceBinding = new ServiceInstanceBinding(bindingId, serviceInstance.getId(),
                credentials, null);
        serviceInstanceBinding.setExternalServerAddresses(externalAddresses);
        return serviceInstanceBinding;
    }

    @Override
    protected RouteBinding bindRoute(ServiceInstance serviceInstance, String route) {
        throw new UnsupportedOperationException();
    }



}
