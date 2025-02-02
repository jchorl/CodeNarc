---
layout: default
title: CodeNarc - JUnit Rules
---

# JUnit Rules  ("*rulesets/junit.xml*")


## ChainedTest Rule

*Since CodeNarc 0.13*

A test method that invokes another test method is a chained test; the methods are dependent on one another.
Tests should be isolated, and not be dependent on one another.

Example of violations:

```
    class MyTest extends GroovyTestCase {
        public void testFoo() {

            // violations, calls test method on self
            5.times { testBar() }
            5.times { this.testBar() }

            // OK, no violation: one arg method is not actually a test method
            5.times { testBar(it) }
        }

        private static void assertSomething() {
            testBar() // violation, even if in helper method
            this.testBar() // violation, even if in helper method
        }

        public void testBar() {
            // ...
        }
    }
```


## CoupledTestCase Rule

*Since CodeNarc 0.13*

This rule finds test cases that are coupled to other test cases, either by invoking static methods on another test case
or by creating instances of another test case. If you require shared logic in test cases then extract that logic to a
new class where it can properly be reused. Static references to methods on the current test class are ignored.

Example of violations:

```
    class MyTest extends GroovyTestCase {
        public void testMethod() {
            // violation, static method call to other test
            MyOtherTest.helperMethod()

            // violation, instantiation of another test class
            new MyOtherTest()

            // no violation; same class
            def input = MyTest.getResourceAsStream('sample.txt')
        }
    }
```


## JUnitAssertAlwaysFails Rule

Rule that checks for JUnit `assert()` method calls with constant or literal arguments such that the
assertion always fails. This includes:
  * `assertTrue(false)`
  * `assertTrue(0)`
  * `assertTrue('')`
  * `assertTrue([])`
  * `assertTrue([:])`
  * `assertFalse(true)`
  * `assertFalse('abc')`
  * `assertFalse(99)`
  * `assertFalse([123])`
  * `assertFalse([a:123)`
  * `assertNull(CONSTANT)`.
  * `assertNull([])`.
  * `assertNull([123])`.
  * `assertNull([:])`.
  * `assertNull([a:123])`.

This rule sets the default value of the *applyToClassNames* property to only match class names
ending in 'Spec', 'Test', 'Tests' or 'TestCase'.


## JUnitAssertAlwaysSucceeds Rule

Rule that checks for JUnit `assert()` method calls with constant arguments such that the
assertion always succeeds. This includes:
  * `assertTrue(true)`
  * `assertTrue(99)`
  * `assertTrue('abc')`
  * `assertTrue([123])`
  * `assertTrue([a:123])`
  * `assertFalse(false)`
  * `assertFalse('')`
  * `assertFalse(0)`
  * `assertFalse([])`
  * `assertFalse([:)`
  * `assertNull(null)`

This rule sets the default value of the *applyToClassNames* property to only match class names
ending in 'Spec', 'Test', 'Tests' or 'TestCase'.


## JUnitFailWithoutMessage Rule

*Since CodeNarc 0.11*

This rule detects JUnit calling the `fail()` method without an argument. For better error reporting you
should always provide a message.


## JUnitLostTest Rule

*Since CodeNarc 0.18*

This rule checks for classes that import JUnit 4 classes and contain a `public`, instance, `void`,
no-arg method named *test** that is not abstract and not annotated with the JUnit 4 `@Test` annotation.

Note: This rule should be disabled for Grails 2.x projects, since the Grails test framework can use
AST Transformations to automatically annotate test methods.

This rule sets the default value of the *applyToClassNames* property to only match class names
ending in 'Spec', 'Test', 'Tests' or 'TestCase'.

Example of violations:

```
    import org.junit.Test

    class MyTestCase {
        void testMe() { }           // missing @Test annotation
    }
```


## JUnitPublicField Rule

*Since CodeNarc 0.19*

Checks for public fields on a JUnit test class.  There is usually no reason to have a public
field (even a constant) on a test class.

Fields within interfaces and fields annotated with @Rule are ignored.

This rule sets the default value of the *applyToClassNames* property to only match class names
ending in 'Spec', 'Test', 'Tests' or 'TestCase'.

Example of violations:

```
    import org.junit.Test
    class MyTestCase {
        public int count                        // violation
        public static final MAX_VALUE = 1000    // violation

        @Test
        void testMe() { }
    }
```


## JUnitPublicNonTestMethod Rule

Rule that checks if a JUnit test class contains public methods other than standard test methods,
JUnit framework methods or methods with JUnit annotations.

The following public methods are ignored by this rule:
  * Zero-argument methods with names starting with "test"
  * The `setUp()` and `tearDown()` methods
  * Methods annotated with `@Test`
  * Methods annotated with `@Before`, `@BeforeAll`, `@BeforeClass` and `@BeforeEach`
  * Methods annotated with `@After`, `@AfterAll`, `@AfterClass` and `@AfterEach`
  * Methods annotated with `@Disabled` and `@Ignore`
  * Methods annotated with `@Override`

Public, non-test methods on a test class violate conventional usage of test classes,
and they typically break encapsulation unnecessarily.

Public, non-test methods may also hide unintentional *'Lost Tests'*. For instance, the test method
declaration may (unintentionally) include methods parameters, and thus be ignored by JUnit. Or the
method may (unintentionally) not follow the "test.." naming convention and not have the @Test annotation,
and thus be ignored by JUnit.

This rule sets the default value of the *applyToClassNames* property to only match class names
ending in 'Spec', 'Test', 'Tests' or 'TestCase'.

| Property                    | Description            | Default Value    |
|-----------------------------|------------------------|------------------|
| ignoreMethodsWithAnnotations | Specifies one or more (comma-separated) annotation names. Methods annotated with the annotations are ignored by this rule.  | After,AfterAll,AfterClass, AfterEach,Before,BeforeAll, BeforeClass,BeforeEach, Disabled,Ignore, Override,Test |


## JUnitPublicProperty Rule

*Since CodeNarc 0.21*

Checks for public properties defined on JUnit test classes. There is typically no need to
expose a public property (with public *getter* and *setter* methods) on a test class.

This rule sets the default value of the *applyToClassNames* property to only match class names
ending in 'Spec', 'Test', 'Tests' or 'TestCase'.

| Property                    | Description            | Default Value    |
|-----------------------------|------------------------|------------------|
| ignorePropertyNames         | Specifies one or more (comma-separated) property names that should be ignored (i.e., that should not cause a rule violation). The names may optionally contain wildcards (*,?).  | `null` |

Example of violations:

```
    import org.junit.Test
    class MyTestCase {
        static String id    // violation
        def helper          // violation
        String name         // violation

        @Test
        void testMe() { }
    }
```


## JUnitSetUpCallsSuper Rule

Rule that checks that if the JUnit `setUp` method is defined, that it includes a call to
`super.setUp()`.

This rule ignored methods annotated with `@Before` or `@BeforeClass`.

This rule sets the default value of the *applyToClassNames* property to only match class names
ending in 'Spec', 'Test', 'Tests' or 'TestCase'.


## JUnitStyleAssertions Rule

*Since CodeNarc 0.11*

This rule detects calling JUnit style assertions like `assertEquals`, `assertTrue`,
`assertFalse`, `assertNull`, `assertNotNull`. Groovy 1.7 ships with a feature called the
"power assert", which is an assert statement with better error reporting. This is preferable to the
JUnit assertions.


## JUnitTearDownCallsSuper Rule

Rule that checks that if the JUnit `tearDown` method is defined, that it includes a call to
`super.tearDown()`.

This rule ignored methods annotated with `@After` or `@AfterClass`.

This rule sets the default value of the *applyToClassNames* property to only match class names
ending in 'Spec', 'Test', 'Tests' or 'TestCase'.


## JUnitTestMethodWithoutAssert Rule

*Since CodeNarc 0.12*

This rule searches for test methods that do not contain assert statements. Either the test method is missing assert
statements, which is an error, or the test method contains custom assert statements that do not follow a proper assert
naming convention. Test methods are defined as public void methods that begin with the work test or have a @Test
annotation. By default this rule applies to the default test class names, but this can be changed using the rule's
applyToClassNames property. An assertion is defined as either using the `assert` keyword or invoking a method that
starts with the work assert, like assertEquals, assertNull, or assertMyClassIsSimilar. Also, any method named
`should.*` also counts as an assertion so that `shouldFail` methods do not trigger an assertion, any method
that starts with `fail** counts as an assertion, and any method that starts with `verify` counts as an assertion.
Since version 0.23 CodeNarc has support for JUnit's ExpectedException.

What counts as an assertion method can be overridden using the assertMethodPatterns property of the rule. The
default value is this comma separated list of regular expressions:

```
    String assertMethodPatterns = 'assert.*,should.*,fail.*,verify.*,expect.*'
```

If you'd like to add any method starting with 'ensure' to the ignores then you would set the value to this:

```
    'assert.*,should.*,fail.*,verify.*,ensure.*'
```


## JUnitUnnecessarySetUp Rule

Rule that checks checks for JUnit `setUp()` methods that contain only a call to
`super.setUp()`. The method is then unnecessary.

This rule sets the default value of the *applyToClassNames* property to only match class names
ending in 'Spec', 'Test', 'Tests' or 'TestCase'.

Here is an example of a violation:

```
    class MyTest extends TestCase {
        void setUp() {              // violation
            super.setUp()
        }
    }
```


## JUnitUnnecessaryTearDown Rule

Rule that checks checks for JUnit `tearDown()` methods that contain only a call to
`super.tearDown()`. The method is then unnecessary.

This rule sets the default value of the *applyToClassNames* property to only match class names
ending in 'Spec', 'Test', 'Tests' or 'TestCase'.

Here is an example of a violation:

```
    class MyTest extends TestCase {
        void tearDown() {               // violation
            super.tearDown()
        }
    }
```


## JUnitUnnecessaryThrowsException Rule

*Since CodeNarc 0.18*

Check for `throws` clauses on JUnit test methods. That is not necessary in Groovy.

This rule sets the default value of the *applyToClassNames* property to only match class names
ending in 'Spec', 'Test', 'Tests' or 'TestCase'.

Example of violations:

```
    @Test
    void shouldDoStuff() throws Exception { }           // violation

    @BeforeClass void initialize() throws Exception { } // violation
    @Before void setUp() throws RuntimeException { }    // violation
    @After void tearDown() throws Exception { }         // violation
    @AfterClass void cleanUp() throws Exception { }     // violation
    @Ignore void ignored() throws Exception { }         // violation

    class MyTest extends GroovyTestCase {
        void test1() throws Exception { }               // violation
        public void test2() throws IOException { }      // violation
    }

```


## SpockIgnoreRestUsed Rule

*Since CodeNarc 0.14*

If Spock's `@IgnoreRest` annotation appears on any method, all non-annotated test methods are not executed.
This behaviour is almost always unintended. It's fine to use @IgnoreRest locally during development, but when
committing code, it should be removed.

The *specificationClassNames* and *specificationSuperclassNames* properties determine which classes are considered
Spock *Specification* classes.

| Property                    | Description            | Default Value    |
|-----------------------------|------------------------|------------------|
| specificationClassNames     | Specifies one or more (comma-separated) class names that should be treated as Spock Specification classes. The class names may optionally contain wildcards (*,?), e.g. "*Spec". | `null` |
| specificationSuperclassNames| Specifies one or more (comma-separated) class names that should be treated as Spock Specification superclasses. In other words, a class that extends a matching class name is considered a Spock Specification . The class names may optionally contain wildcards (*,?), e.g. "*Spec". | "*Specification" |

Example of violations:

```
    public class MySpec extends spock.lang.Specification {
        @spock.lang.IgnoreRest
        def "my first feature"() {
            expect: false
        }

        def "my second feature"() {
            given: def a = 2

            when: a *= 2

            then: a == 4
        }
    }
```


## SpockMissingAssert Rule

*Since CodeNarc 3.3.0*

Spock treats all expressions on the first level of a then or expect block as an implicit assertion.
However, everything inside if/for/switch/... blocks is not an implicit assert, just a useless comparison (unless wrapped by a `with` or `verifyAll`).

This rule finds such expressions, where an explicit call to `assert` would be required. Please note that the rule might
produce false positives, as it relies on method names to determine whether an expression has a boolean type or not.

Example of violations:

```
    public class MySpec extends spock.lang.Specification {
        def "test passes - does not behave as expected"() {
            expect:
            if (true) {
                true == false // violation - is inside an if block, and therefore not treated as an implicit assertion by spock
            }
        }

        def "test fails - behaves as expected"() {
            expect:
            if (true) {
                with(new Object()) {
                    true == false // no violation - expressions in with are treated as implicit assertions by spock
                }
            }
        }
    }
```

| Property                    | Description            | Default Value    |
|-----------------------------|------------------------|------------------|
| specificationClassNames     | Specifies one or more (comma-separated) class names that should be treated as Spock Specification classes. The class names may optionally contain wildcards (*,?), e.g. "*Spec". | `null` |
| specificationSuperclassNames| Specifies one or more (comma-separated) class names that should be treated as Spock Specification superclasses. In other words, a class that extends a matching class name is considered a Spock Specification . The class names may optionally contain wildcards (*,?), e.g. "*Spec". | "*Specification" |


## UnnecessaryFail Rule

*Since CodeNarc 0.13*

In a unit test, catching an exception and immediately calling Assert.fail() is pointless and hides the stack trace.
It is better to rethrow the exception or not catch the exception at all.

This rule sets the default value of the *applyToClassNames* property to only match class names
ending in 'Spec', 'Test', 'Tests' or 'TestCase'.

Example of violations:

```
    public void testSomething() {
        try {
            something()
        } catch (Exception e) {
            fail(e.message)
        }

        try {
            something()
        } catch (Exception e) {
            fail()
        }
    }
```


## UseAssertEqualsInsteadOfAssertTrue Rule

*Since CodeNarc 0.11*

This rule detects JUnit assertions in object equality. These assertions should be made by more specific methods,
like `assertEquals`.

This rule sets the default value of the *applyToClassNames* property to only match class names
ending in 'Spec', 'Test', 'Tests' or 'TestCase'.


## UseAssertFalseInsteadOfNegation Rule

*Since CodeNarc 0.12*

In unit tests, if a condition is expected to be false then there is no sense using `assertTrue` with the negation operator.
For instance, `assertTrue(!condition)` can always be simplified to `assertFalse(condition)`.

This rule sets the default value of the *applyToClassNames* property to only match class names
ending in 'Spec', 'Test', 'Tests' or 'TestCase'.


## UseAssertTrueInsteadOfAssertEquals Rule

*Since CodeNarc 0.11*

This rule detects JUnit calling `assertEquals` where the first parameter is a boolean. These assertions
should be made by more specific methods, like `assertTrue` or `assertFalse`.

This rule sets the default value of the *applyToClassNames* property to only match class names
ending in 'Spec', 'Test', 'Tests' or 'TestCase'.

| Property                    | Description            | Default Value    |
|-----------------------------|------------------------|------------------|
| checkAssertStatements       | If `true`, then also check assert statements, e.g. `assert x == true`. | `false` |


All of the following examples can be simplified to assertTrue or remove the true literal:

```
    assertEquals(true, foo())
    assertEquals("message", true, foo())
    assertEquals(foo(), true)
    assertEquals("message", foo(), true)
    assertEquals(false, foo())
    assertEquals("message", false, foo())
    assertEquals(foo(), false)
    assertEquals("message", foo(), false)

    assert true == foo()                    // violation only if checkAssertStatements == true
    assert foo() == true : "message"        // violation only if checkAssertStatements == true
    assert false == foo()                   // violation only if checkAssertStatements == true
    assert foo() == false : "message"       // violation only if checkAssertStatements == true
```


## UseAssertTrueInsteadOfNegation Rule

*Since CodeNarc 0.12*

In unit tests, if a condition is expected to be true then there is no sense using `assertFalse` with the negation operator.
For instance, `assertFalse(!condition)` can always be simplified to `assertTrue(condition)`.

This rule sets the default value of the *applyToClassNames* property to only match class names
ending in 'Spec', 'Test', 'Tests' or 'TestCase'.


## UseAssertNullInsteadOfAssertEquals Rule

*Since CodeNarc 0.11*

This rule detects JUnit calling `assertEquals` where the first or second parameter is `null`.
These assertion should be made against the `assertNull` method instead.

This rule sets the default value of the *applyToClassNames* property to only match class names
ending in 'Spec', 'Test', 'Tests' or 'TestCase'.


## UseAssertSameInsteadOfAssertTrue Rule

*Since CodeNarc 0.11*

This rule detects JUnit calling `assertTrue` or `assertFalse` where the first or second parameter
is an `Object#is()` call testing for reference equality. These assertion should be made against the
`assertSame` or `assertNotSame` method instead.

This rule sets the default value of the *applyToClassNames* property to only match class names
ending in 'Spec', 'Test', 'Tests' or 'TestCase'.

