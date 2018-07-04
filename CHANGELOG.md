# Changelog
All notable changes to this project will be documented in this file.

## Unreleased

### Added
- Customisation of subtype names with `@SubTypeName`.
- Customisation of field names with `@DatastoreFieldName`.
- `pure` and `failure` on `DatastoreOperation`
- `safeDelete` and `safeDeleteEntity` operations
- Ability to use nested entities as fields

### Breaking Changes
- Made ValueFormat a sealed trait. You should never need to implement your own value formats, the existing
functions such as `formatFrom` are enough to create custom formats.
- Rename `asException` to `asThrowable` on `DatastoreError`, simply rename if you are using this function.

## 0.1.6 2018-05-13
### Added
- Support for Transactional operations

### Removed
 - `formatFromFunctions` and `formatFromFunctionsEither` functions after deprecation 

## 0.1.5 2018-05-11

### Added
- `deleteEntity` and `deleteAllEntities` operations
- `orderByAscending` and `orderByDescending` for queries
- `limit` for queries

### Deprecated
- `formatFromFunctions` in favor of `formatFrom`
- `formatFromFunctionsEither` in favor of `failableFormatFrom`

### Breaking Changes
- Rename `DataStoreConfiguration` to `DatastoreConfiguration` for consistency, this includes the `datastoreConfiguration`
function on `DatastoreRepository`. Simply rename the function in your repository and any uses of the `DatastoreConfiguration`
type.

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
