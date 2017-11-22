# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]
- cleaned up IdentityProvider class


## [0.9.0]  - 2017-10-23
### Changed
- using AppAuth 0.7.0
- updated error codes a bit

## [0.3.2]  - 2017-10-23
### Added
- added to SessionManager diskDataReset() and dataReset()
- added to SessionManager registerForEvent() and unregisterAllEvents
- added specific error callback for logout
- added SESSION_REFRESH_EXPIRED_SESSION_ERROR; offline notification change
- added  per Activity IntentReceiver allocated/deallocated in (add|Remove)SessionListener
- initialized() now returns an offline session if there is no network
- added listener callbacks didLoseNetwork, didGainNetwork for network connectivity changes
- support for offline mode
- added listener callbacks for  didLoseSession(), didGainSession()
- additional parameters for authorize function, e.g. for prompt and acr_values

### Changed
- Switched the example app to use virtual card backend instead of Qvarn
- changed Data.getContext() to .getAppContext(); SessionManager.authorize() now requires activity context as parameter
- updated Session to reflect ongoing status changes
- fix for AuthState slowly growing to epic sizes by updating AppAuth to 0.6.1

## [0.2.0] - 2017-06-21
### Added
- License information to readme (Apache 2)
- Changelog

### Changed
- Renamed *performLogoutRequest* to *logout* in *SessionManager*
- Refence installation for vaultit-mobilessosdk-0.2-release.aar
- logout() does refresh automatically before attempting logout
- Intent return values are now Session and SessionError objects
- Rewrite decodeJWTToken()

## [0.1.0] - 2017-05-12
### Added
- First preview release based on App-Auth 0.6.0
