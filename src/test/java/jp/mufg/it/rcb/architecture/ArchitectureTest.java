package jp.mufg.it.rcb.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * クリーン/ヘキサゴナルアーキテクチャの依存方向と禁則を保証する.
 *
 * <p>アプリ側パッケージ（domain / application / adapter / shared）に対する依存ルールを定義し、 社内ライブラリ準拠 IF
 * パッケージ（userinfo.dto / exception / log.cdi / log.formatter）は allowlist する.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArchitectureTest {

  /** main 配下の全クラスをインポートした結果. {@code @BeforeAll} で 1 度だけ初期化する. */
  private JavaClasses classes;

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ ArchitectureTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** main 配下のクラスのみをスキャン対象としてインポートする. */
  @BeforeAll
  void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("jp.mufg.it.rcb");
  }

  /** ドメイン層は java / lombok / userinfo.dto 以外に依存してはならない. */
  @Test
  void domainPurity() {
    final ArchRule rule =
        classes()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                "..domain..", "java..", "lombok..", "jp.mufg.it.rcb.userinfo.dto..");
    rule.check(classes);
  }

  /** アプリケーション層は adapter / 永続化 / JSF / PrimeFaces に依存してはならない. */
  @Test
  void applicationBoundary() {
    final ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..adapter..", "jakarta.persistence..", "jakarta.faces..", "primefaces..");
    rule.check(classes);
  }

  /** Inbound アダプタは Outbound アダプタに依存してはならない. */
  @Test
  void adapterInputDoesNotDependOnAdapterOutput() {
    final ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..adapter.in..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..adapter.out..");
    rule.check(classes);
  }

  /** アプリケーション層に JSF のビュースコープや CDI Named は付与してはならない. */
  @Test
  void applicationLayerHasNoViewOrNamedScopes() {
    final ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .beAnnotatedWith("jakarta.faces.view.ViewScoped")
            .orShould()
            .beAnnotatedWith("jakarta.inject.Named");
    rule.check(classes);
  }

  /** ドメイン層は永続化 API（jakarta.persistence）に依存してはならない. */
  @Test
  void domainHasNoPersistenceDependency() {
    final ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("jakarta.persistence..");
    rule.check(classes);
  }

  /** アプリ側パッケージは社内ライブラリ準拠 IF 専用の config パッケージに依存してはならない. */
  @Test
  void configPackageConfinedToInternalLibraryIfPackages() {
    final ArchRule rule =
        noClasses()
            .that()
            .resideInAnyPackage("..domain..", "..application..", "..adapter..", "..shared..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("jp.mufg.it.rcb.config..");
    rule.check(classes);
  }

  /** アプリ側パッケージは社内ライブラリ準拠 IF の log.formatter パッケージに依存してはならない. */
  @Test
  void logFormatterPackageConfinedToInternalLibraryIfPackages() {
    final ArchRule rule =
        noClasses()
            .that()
            .resideInAnyPackage("..domain..", "..application..", "..adapter..", "..shared..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("jp.mufg.it.rcb.log.formatter..");
    rule.check(classes);
  }
}
