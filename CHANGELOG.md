# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Branching model (`main` / `develop` / `test` / `feature/*` / `release/*`), pull request template and this changelog.

### Changed

### Fixed

- Operators could read, view segment images of, and resolve other operators' documents by guessing the ID; access is now scoped to the uploader (admins unaffected).
- Unmatched routes, wrong HTTP methods, missing parameters, and invalid path variable types now return proper 404/405/400 error codes instead of a generic 500.
- Template preview no longer crashes with a 500 when a segment is missing required coordinates; it now returns 400 with a clear message.

### Removed