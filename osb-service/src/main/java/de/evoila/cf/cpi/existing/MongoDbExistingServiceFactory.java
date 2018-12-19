/**
 * 
 */
package de.evoila.cf.cpi.existing;

import de.evoila.cf.broker.bean.ExistingEndpointBean;
import de.evoila.cf.broker.custom.mongodb.MongoDBCustomImplementation;
import de.evoila.cf.broker.custom.mongodb.MongoDbService;
import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.model.Platform;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.repository.PlatformRepository;
import de.evoila.cf.broker.service.availability.ServicePortAvailabilityVerifier;
import de.evoila.cf.broker.util.RandomString;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author René Schollmeyer
 *
 */
@Service
@ConditionalOnBean(ExistingEndpointBean.class)
public class MongoDbExistingServiceFactory extends ExistingServiceFactory {

    RandomString usernameRandomString = new RandomString(10);
    RandomString passwordRandomString = new RandomString(15);

    private ExistingEndpointBean existingEndpointBean;

	private MongoDBCustomImplementation mongoDBCustomImplementation;

    public MongoDbExistingServiceFactory(PlatformRepository platformRepository,
                                         ServicePortAvailabilityVerifier portAvailabilityVerifier,
                                         ExistingEndpointBean existingEndpointBean,
                                         MongoDBCustomImplementation mongoDBCustomImplementation) {
        super(platformRepository, portAvailabilityVerifier, existingEndpointBean);
        this.existingEndpointBean = existingEndpointBean;
        this.mongoDBCustomImplementation = mongoDBCustomImplementation;
    }

    @Override
    public void deleteInstance(ServiceInstance serviceInstance, Plan plan) throws PlatformException {
        MongoDbService mongoDbService = this.connection(serviceInstance, plan);

        mongoDBCustomImplementation.deleteDatabase(mongoDbService, serviceInstance.getId());
    }

    @Override
    public ServiceInstance createInstance(ServiceInstance serviceInstance, Plan plan, Map<String, Object> parameters) throws PlatformException {

        String username = usernameRandomString.nextString();
        String password = passwordRandomString.nextString();

        serviceInstance.setUsername(username);
        serviceInstance.setPassword(password);

        MongoDbService mongoDbService = this.connection(serviceInstance, plan);

        mongoDBCustomImplementation.createDatabase(mongoDbService, serviceInstance.getId());

        return serviceInstance;
    }

    private MongoDbService connection(ServiceInstance serviceInstance, Plan plan) {
        MongoDbService jdbcService = new MongoDbService();

        if (plan.getPlatform() == Platform.EXISTING_SERVICE)
            jdbcService.createConnection(existingEndpointBean.getUsername(), existingEndpointBean.getPassword(),
                    existingEndpointBean.getDatabase(), existingEndpointBean.getHosts());

        return jdbcService;
    }
}
