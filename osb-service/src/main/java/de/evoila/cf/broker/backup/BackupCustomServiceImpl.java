package de.evoila.cf.broker.backup;

import com.mongodb.MongoException;
import com.mongodb.client.ListDatabasesIterable;
import de.evoila.cf.broker.custom.mongodb.MongoDBCustomImplementation;
import de.evoila.cf.broker.custom.mongodb.MongoDBService;
import de.evoila.cf.broker.custom.mongodb.MongoDBUtils;
import de.evoila.cf.broker.exception.ServiceBrokerException;
import de.evoila.cf.broker.exception.ServiceDefinitionDoesNotExistException;
import de.evoila.cf.broker.exception.ServiceDefinitionPlanDoesNotExistException;
import de.evoila.cf.broker.exception.ServiceInstanceDoesNotExistException;
import de.evoila.cf.broker.model.Platform;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.model.credential.UsernamePasswordCredential;
import de.evoila.cf.broker.repository.ServiceDefinitionRepository;
import de.evoila.cf.broker.repository.ServiceInstanceRepository;
import de.evoila.cf.broker.service.BackupCustomService;
import de.evoila.cf.cpi.CredentialConstants;
import de.evoila.cf.security.credentials.CredentialStore;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Johannes Hiemer.
 */
@Service
public class BackupCustomServiceImpl implements BackupCustomService {

    private ServiceInstanceRepository serviceInstanceRepository;

    private MongoDBCustomImplementation mongoDBCustomImplementation;

    private ServiceDefinitionRepository serviceDefinitionRepository;

    private CredentialStore credentialStore;

    public BackupCustomServiceImpl(ServiceInstanceRepository serviceInstanceRepository,
                                   MongoDBCustomImplementation mongoDBCustomImplementation,
                                   ServiceDefinitionRepository serviceDefinitionRepository,
                                   CredentialStore credentialStore) {
        this.serviceInstanceRepository = serviceInstanceRepository;
        this.mongoDBCustomImplementation = mongoDBCustomImplementation;
        this.serviceDefinitionRepository = serviceDefinitionRepository;
        this.credentialStore = credentialStore;
    }

    @Override
    public Map<String, String> getItems(String serviceInstanceId) throws ServiceInstanceDoesNotExistException,
            ServiceDefinitionDoesNotExistException, ServiceDefinitionPlanDoesNotExistException {
        ServiceInstance serviceInstance = serviceInstanceRepository.getServiceInstance(serviceInstanceId);

        if(serviceInstance == null || serviceInstance.getHosts().size() <= 0) {
            throw new ServiceInstanceDoesNotExistException(serviceInstanceId);
        }

        Plan plan = serviceDefinitionRepository.getPlan(serviceInstance.getServiceDefinitionId(),serviceInstance.getPlanId());

        Map<String, String> result = new HashMap<>();
        if (plan.getPlatform().equals(Platform.BOSH)) {
            UsernamePasswordCredential usernamePasswordCredential = credentialStore.getUser(serviceInstance, CredentialConstants.ROOT_CREDENTIALS);
            MongoDBService mongoDbService = mongoDBCustomImplementation.connection(serviceInstance, plan, usernamePasswordCredential);

            try {
                ListDatabasesIterable<Document> databases = mongoDbService.mongoClient().listDatabases();

                for (Document database : databases)
                    result.put(database.getString("name"), database.getString("name"));
            } catch (MongoException ex) {
                new ServiceBrokerException("Could not load databases", ex);
            }
        } else if (plan.getPlatform().equals(Platform.EXISTING_SERVICE)) {
            result.put(MongoDBUtils.dbName(serviceInstance.getId()), MongoDBUtils.dbName(serviceInstance.getId()));
        }

        return result;
    }

}
