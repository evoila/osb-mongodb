# Changelog

## 27.11.2024 - v1.3.5
- OSB-Mongodb 1.3.5
- OSB-Bosh-Mongodb 0.3.3

### Feature
- new mongodb.tls.allowConnectionsWithoutCertificates property
- new mongodb.tls.allowInvalidCertificates property

## 15.11.2024 - v1.3.4
- OSB-Mongodb 1.3.4
- OSB-Bosh-Mongodb 0.3.2

### Fix
- Re-deployment post-deploy replica set fix
- Database name duplicates in bosh manifest fix
- Dashboard title correctly states MongoDB
- Fix for custom CA certificate in service credentials 

## 8.11.2024 - v1.3.3
- OSB-Mongodb 1.3.3
- OSB-Bosh-Mongodb 0.3.0

### Feature
- The service instance plan now can be upgraded
- Support for the latest MongoDB versions: 6.0.19, 7.0.15, 8.0.3
- Updated OSB-MongoDB dependencies for the latest Spring Boot 3.3.3
- Support for Ubuntu Jammy stemcell

## 11.10.2024
- OSB-MongoDB 1.2.4
- OSB-Bosh-MongoDB 0.2.21

### Feature
- OSB-MongoDB now deploys the Boshreleases on Ubuntu Jammy
- Bosh-Deployments use MongoDB 6

## 26.07.2024
- OSB-MongoDB 1.2.3

### Feature
- The OSB-MongoDB now generates Properties in Bosh manifests the same way, the other OSBs (like OSB-PostgreSQL) do. This means that, instead of generating properties at instance_groups[mongodb].jobs[mongodb].properties, properties are now generated at instance_groups[mongodb].properties. This allows additional properties coming from the catalog to be merged correctly in the Bosh manifest.
