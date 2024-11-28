package de.evoila.cf.cpi.existing;

import de.evoila.cf.broker.bean.ExistingEndpointBean;
import de.evoila.cf.broker.custom.mongodb.MongoDBCustomImplementation;
import de.evoila.cf.broker.custom.mongodb.MongoDBService;
import de.evoila.cf.broker.custom.mongodb.MongoDBUtils;
import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.model.credential.UsernamePasswordCredential;
import de.evoila.cf.broker.repository.PlatformRepository;
import de.evoila.cf.broker.service.availability.ServicePortAvailabilityVerifier;
import de.evoila.cf.cpi.CredentialConstants;
import de.evoila.cf.security.credentials.CredentialStore;
import de.evoila.cf.security.credentials.DefaultCredentialConstants;
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
        if (existingEndpointBean.getBackupCredentials() != null)
            credentialStore.createUser(serviceInstance, DefaultCredentialConstants.BACKUP_AGENT_CREDENTIALS,
                    existingEndpointBean.getBackupCredentials().getUsername(), existingEndpointBean.getBackupCredentials().getPassword());

        credentialStore.createUser(serviceInstance, CredentialConstants.ROOT_CREDENTIALS);
        UsernamePasswordCredential serviceInstanceUsernamePasswordCredential = credentialStore.getUser(serviceInstance, CredentialConstants.ROOT_CREDENTIALS);

        credentialStore.createUser(serviceInstance, DefaultCredentialConstants.BACKUP_CREDENTIALS, serviceInstanceUsernamePasswordCredential.getUsername(),
                serviceInstanceUsernamePasswordCredential.getPassword());

        serviceInstance.setUsername(serviceInstanceUsernamePasswordCredential.getUsername());

        MongoDBService mongoDbService = mongoDBCustomImplementation.connection(serviceInstance, plan, null);

        mongoDBCustomImplementation.createDatabase(mongoDbService, MongoDBUtils.dbName(serviceInstance.getId()));
        mongoDbService.close();

        return serviceInstance;
    }

    @Override
    public void deleteInstance(ServiceInstance serviceInstance, Plan plan) throws PlatformException {
        MongoDBService mongoDbService = mongoDBCustomImplementation.connection(serviceInstance, plan, null);

        mongoDBCustomImplementation.deleteDatabase(mongoDbService, MongoDBUtils.dbName(serviceInstance.getId()));
        mongoDbService.close();

        if(existingEndpointBean.getBackupCredentials() != null) {
            credentialStore.deleteCredentials(serviceInstance, DefaultCredentialConstants.BACKUP_AGENT_CREDENTIALS);
        }

        try {
            credentialStore.deleteCredentials(serviceInstance, CredentialConstants.ROOT_CREDENTIALS);
            credentialStore.deleteCredentials(serviceInstance, CredentialConstants.EXPORTER_CREDENTIALS);
            credentialStore.deleteCredentials(serviceInstance, DefaultCredentialConstants.BACKUP_CREDENTIALS);
            credentialStore.deleteCertificate(serviceInstance, CredentialConstants.SERVER_CERT);
            credentialStore.deleteCertificate(serviceInstance, CredentialConstants.SERVER_CA);
        }
        catch (Exception ex) {
            log.warn("Exception deleting credentials and certificates: {}",ex.getMessage());
        }
    }

    @Override
    public ServiceInstance getInstance(ServiceInstance serviceInstance, Plan plan) {
        return serviceInstance;
    }

}
