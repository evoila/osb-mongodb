package de.evoila.cf.cpi.existing;

import de.evoila.cf.broker.bean.ExistingEndpointBean;
import de.evoila.cf.broker.custom.mongodb.MongoDBCustomImplementation;
import de.evoila.cf.broker.custom.mongodb.MongoDbService;
import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.model.credential.UsernamePasswordCredential;
import de.evoila.cf.broker.repository.PlatformRepository;
import de.evoila.cf.broker.service.availability.ServicePortAvailabilityVerifier;
import de.evoila.cf.cpi.CredentialConstants;
import de.evoila.cf.security.credentials.CredentialStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author René Schollmeyer, Johannes Hiemer.
 */
@Service
@ConditionalOnBean(ExistingEndpointBean.class)
public class MongoDbExistingServiceFactory extends ExistingServiceFactory {

    private ExistingEndpointBean existingEndpointBean;

	private MongoDBCustomImplementation mongoDBCustomImplementation;

	private CredentialStore credentialStore;

    public MongoDbExistingServiceFactory(PlatformRepository platformRepository,
                                         ServicePortAvailabilityVerifier portAvailabilityVerifier,
                                         ExistingEndpointBean existingEndpointBean,
                                         MongoDBCustomImplementation mongoDBCustomImplementation,
                                         CredentialStore credentialStore) {
        super(platformRepository, portAvailabilityVerifier, existingEndpointBean);
        this.existingEndpointBean = existingEndpointBean;
        this.mongoDBCustomImplementation = mongoDBCustomImplementation;
        this.credentialStore = credentialStore;
    }

    @Override
    public ServiceInstance createInstance(ServiceInstance serviceInstance, Plan plan, Map<String, Object> parameters) throws PlatformException {
        UsernamePasswordCredential usernamePasswordCredential = credentialStore.createUser(serviceInstance, CredentialConstants.ROOT_CREDENTIALS);
        serviceInstance.setUsername(usernamePasswordCredential.getUsername());

        MongoDbService mongoDbService = mongoDBCustomImplementation.connection(serviceInstance, plan, null);

        mongoDBCustomImplementation.createDatabase(mongoDbService, serviceInstance.getId());

        return serviceInstance;
    }

    @Override
    public void deleteInstance(ServiceInstance serviceInstance, Plan plan) throws PlatformException {
        credentialStore.deleteCredentials(serviceInstance, CredentialConstants.ROOT_CREDENTIALS);

        MongoDbService mongoDbService = mongoDBCustomImplementation.connection(serviceInstance, plan, null);

        mongoDBCustomImplementation.deleteDatabase(mongoDbService, serviceInstance.getId());
    }

    @Override
    public ServiceInstance getInstance(ServiceInstance serviceInstance, Plan plan) {
        return serviceInstance;
    }

}
