# client

Contains the general, public client definitions for Backfila so clients and the service can 
communicate. Never use this directly, instead use the specialized clients.

Java/Kotlin
Client - public API for communicating with Backfila, Customers depend on this for general backfila features (Parameters, dry run, logging)
Client-<service framework> - provides the base interaction with backfila for your service framework(Logging setup, client service, registration on startup). You install one of these in your real implementation. backfila-embedded is your test implementation.
Client-base - base functionality that all downstream datasource clients need (implementions of common features, parameters, Operator caching). Provides and SPI for those clients to satisfy in order to get this base functionality. This is private. Customers cannot depend on this. Only datasource clients depend on this.
client-<specific datasouce> - The specific datasouce Backfila implementation. This is where the ergonomics of working with your particular datasource exist 
