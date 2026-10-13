# Third-party notices

Project source is covered by the [MIT license](LICENSE). Dependencies retain their own licenses.

The application uses Spring Boot, Spring Framework, Spring Security and Thymeleaf through Maven dependencies. Test dependencies and transitive libraries are resolved by the [POM](pom.xml). Their authoritative license information is provided by the respective dependency distributions; this project's MIT license does not replace it. Inspect the resolved dependency set with:

```sh
mvn dependency:tree
```

The container uses an Eclipse Temurin Java runtime. Its distribution includes its own license and notice files.

The stylesheet in `src/main/resources/static/assets/portal.css` is local project code. No third-party browser script or stylesheet is bundled. The Docnaut logo is provided for this application's branding. The source license does not grant rights to Docnaut or EaglesSoft trademarks or imply endorsement of modified distributions.
