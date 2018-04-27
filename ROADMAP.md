# Datastore4s Roadmap

This is a list of features we would ideally like to add. They are not guaranteed future features and some may not be possible.

- [x] Complete version 0.1 with basic datastore CRUD functionality and type class generation
- [x] Support for batch operations
- [ ] Improvement of implicit not found error messages
- [ ] Support for default values of fields
- [ ] Support for subtype queries. In the case of sealed traits, it would be good to be able to query/find only one subtype
- [ ] Support for transactions
- [ ] Support for Entity fields and entity groups

- [ ] Support for automatic Index generation? (OPTIONAL)
- [ ] Support for field filtering on queries? (OPTIONAL)
- [ ] Move to github wiki? (OPTIONAL)
- [ ] Add option to automatically generate formats where possible by importing auto._ ? (OPTIONAL)
- [ ] Migration package to support reading from and migrating a kind to a new kind (OPTIONAL)
- [ ] Add a cats integration module so this can be used with cats (OPTIONAL)
- [ ] Support for combined batch operations e.g. `batch().put(entitiy).save(anotherEntity).delete(key)`? (OPTIONAL)
