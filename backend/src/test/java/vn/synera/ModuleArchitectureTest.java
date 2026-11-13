package vn.synera;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ModuleArchitectureTest {
  @Test
  void featureInternalsStayPrivateToTheirModule() {
    var classes = new ClassFileImporter().importPackages("vn.synera");
    for (String module :
        new String[] {
          "iam",
          "catalog",
          "policy",
          "sellin",
          "credit",
          "inventory",
          "delivery",
          "closing",
          "demand",
          "ticket",
          "trademkt",
          "performance",
          "crm",
          "route",
          "salesorder",
          "display",
          "notify",
          "integration"
        })
      noClasses()
          .that()
          .resideOutsideOfPackage("vn.synera." + module + "..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "vn.synera." + module + ".domain..", "vn.synera." + module + ".repo..")
          .check(classes);
  }
}
