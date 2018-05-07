# Changelog
All notable changes to this project will be documented in this file.

## Unreleased

### Deprecated
- `formatFromFunctions` in favor of `formatFrom`
- `formatFromFunctionsEither` in favor of `failableFormatFrom`

### Breaking Changes
- Rename `DataStoreConfiguration` to `DatastoreConfiguration` for consistency, this includes the `datastoreConfiguration`
function on `DatastoreRepository`.

## 0.1.4 2018-04-27 (First public release)
### Added
- Format for `Set[A]`
- Format for `Float`
- Batch operations `putAll`, `saveAll` and `deleteAll`.

## 0.1.3 - 2018-04-20
### Added
- Ability to customise which top level properties are indexed at `EntityFormat` level.

## 0.1.2 - 2018-04-16
### Added
- Automatic reconfiguration of a Repository when connecting to an emulator.

### Removed
- Support for custom credentials files, these are taken from the environment variable now.

## 0.1.1 - 2018-04-14
## Added
- More options for configuration of a Datastore Repository, including fine grained use of `DatastoreOptions`.

## 0.1 - 2018-04-13
### Added
- Basic CRUD functionality for case classes & sealed traits in datastore.
