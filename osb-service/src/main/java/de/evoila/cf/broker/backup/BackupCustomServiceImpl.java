package de.evoila.cf.broker.backup;

import com.mongodb.MongoException;
import com.mongodb.client.ListDatabasesIterable;
import de.evoila.cf.broker.custom.mongodb.MongoDBCustomImplementation;
import de.evoila.cf.broker.custom.mongodb.MongoDbService;
import de.evoila.cf.broker.exception.ServiceBrokerException;
import de.evoila.cf.broker.exception.ServiceDefinitionDoesNotExistException;
import de.evoila.cf.broker.exception.ServiceInstanceDoesNotExistException;
import de.evoila.cf.broker.model.Platform;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.repository.ServiceDefinitionRepository;
import de.evoila.cf.broker.repository.ServiceInstanceRepository;
import de.evoila.cf.broker.service.BackupCustomService;
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

    public BackupCustomServiceImpl(ServiceInstanceRepository serviceInstanceRepository,
                                   MongoDBCustomImplementation mongoDBCustomImplementation,
                                   ServiceDefinitionRepository serviceDefinitionRepository) {
        this.serviceInstanceRepository = serviceInstanceRepository;
        this.mongoDBCustomImplementation = mongoDBCustomImplementation;
        this.serviceDefinitionRepository = serviceDefinitionRepository;
    }

    @Override
    public Map<String, String> getItems(String serviceInstanceId) throws ServiceInstanceDoesNotExistException,
            ServiceDefinitionDoesNotExistException {
        ServiceInstance instance = serviceInstanceRepository.getServiceInstance(serviceInstanceId);

        if(instance == null || instance.getHosts().size() <= 0) {
            throw new ServiceInstanceDoesNotExistException(serviceInstanceId);
        }

        Plan plan = serviceDefinitionRepository.getPlan(instance.getPlanId());


        Map<String, String> result = new HashMap<>();
        if (plan.getPlatform().equals(Platform.BOSH)) {
            MongoDbService mongoDbService = mongoDBCustomImplementation.connection(instance, plan);

            try {
                ListDatabasesIterable<Document> databases = mongoDbService.mongoClient().listDatabases();

                for (Document database : databases)
                    result.put(database.getString("name"), database.getString("name"));
            } catch (MongoException ex) {
                new ServiceBrokerException("Could not load databases", ex);
            }
        }

        return result;
    }

    @Override
    public void createItem(String serviceInstanceId, String name, Map<String, Object> parameters) throws ServiceInstanceDoesNotExistException,
            ServiceDefinitionDoesNotExistException, ServiceBrokerException {
        ServiceInstance instance = serviceInstanceRepository.getServiceInstance(serviceInstanceId);

        if(instance == null || instance.getHosts().size() <= 0) {
            throw new ServiceInstanceDoesNotExistException(serviceInstanceId);
        }

        Plan plan = serviceDefinitionRepository.getPlan(instance.getPlanId());

        if (plan.getPlatform().equals(Platform.BOSH)) {
            MongoDbService mongoDbService = mongoDBCustomImplementation.connection(instance, plan);

            try {
                mongoDBCustomImplementation.createDatabase(mongoDbService, name);
            } catch (Exception ex) {
                throw new ServiceBrokerException("Could not create Database", ex);
            }

        } else
            throw new ServiceBrokerException("Creating items is not allowed in shared plans");

    }

}
