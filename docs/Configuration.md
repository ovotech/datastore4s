# Configuration
 
To configure your `DatastoreRepository` you must override the `dataStoreConfiguration` function. You can provide datastore configuration using one of the following methods:
 
 ## ManualDataStoreConfiguration
  - `projectId` - The ID of the GCP project to connect to.
  - `namespace` - An optional namespace to store your entities under.
  - Environment variable `DATASTORE_EMULATOR_HOST` - If it is provided then the datastore credential is set to NoCredentials to allow to connect to emulator without checking credentials.
  
 ## EmulatorConfiguration
 - `projectId` - The ID of the GCP project to connect to.
 - `namespace` - An optional namespace to store your entities under.
 - `emulatorHost` - If you are using the datastore emulator, this property is needed.

## FromEnvironmentVariables
Environment variables are used to configure the datastore
 - `DATASTORE_PROJECT_ID`. The ID of the GCP project to connect to. 
 - `DATASTORE_NAMESPACE` - An optional namespace to store your entities under.
 - `DATASTORE_EMULATOR_HOST` - Datastore emulator host to connect to. If it is provided then the datastore credential is set to NoCredentials to allow to connect to emulator without checking credentials.
 
 If you need more fine grained control than available here then you can create your own `DatastoreOptions` object which
 can be implicitly converted into a `DatastoreConfiguration` e.g.
 
 ```scala
def dataStoreConfiguration: DataStoreConfiguration = 
  DatastoreOptions.newBuilder().set(/*options*/).build() 
```

[BACK](../README.md)
