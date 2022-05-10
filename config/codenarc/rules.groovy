ruleset {

    description '''
        Groovy RuleSet containing CodeNarc Rules, grouped by category.
        Check https://github.com/CodeNarc/CodeNarc/blob/3f9718c/src/main/resources/rulesets
        for rulesets definition.
        Some rules are disabled as they are bringing too many breaking changes 
        to Jenkins Pipeline code or some rules are super strict 
        and we don't want to bother people that much. 
        It's all about balance.
        '''

    // rulesets/basic.xml
    AssertWithinFinallyBlock
    AssignmentInConditional
    BigDecimalInstantiation
    BitwiseOperatorInConditional
    BooleanGetBoolean
    BrokenNullCheck
    BrokenOddnessCheck
    ClassForName
    ComparisonOfTwoConstants
    ComparisonWithSelf
    ConstantAssertExpression
    ConstantIfExpression
    ConstantTernaryExpression
    DeadCode
    DoubleNegative
    DuplicateCaseStatement
    DuplicateMapKey
    DuplicateSetValue
    EmptyCatchBlock
    EmptyClass
    EmptyElseBlock
    EmptyFinallyBlock
    EmptyForStatement
    EmptyIfStatement
    EmptyInstanceInitializer
    EmptyMethod
    EmptyStaticInitializer
    EmptySwitchStatement
    EmptySynchronizedStatement
    EmptyTryBlock
    EmptyWhileStatement
    EqualsAndHashCode
    EqualsOverloaded
    ExplicitGarbageCollection
    ForLoopShouldBeWhileLoop
    HardCodedWindowsFileSeparator
    HardCodedWindowsRootDirectory
    IntegerGetInteger
    MultipleUnaryOperators
    RandomDoubleCoercedToZero
    RemoveAllOnSelf
    ReturnFromFinallyBlock
    ThrowExceptionFromFinallyBlock

    // rulesets/braces.xml
    ElseBlockBraces
    ForStatementBraces
    IfStatementBraces
    WhileStatementBraces

    // rulesets/convention.xml
    ConfusingTernary
    CouldBeElvis
    CouldBeSwitchStatement
//    FieldTypeRequired -> nice to have, but pipeline steps
    HashtableIsObsolete
    IfStatementCouldBeTernary
    InvertedCondition
    InvertedIfElse
    LongLiteralWithLowerCaseL
//    MethodParameterTypeRequired -> nice to have, but pipeline steps
//    MethodReturnTypeRequired -> nice to have, but we're not that strict
//    NoDef -> common to use in pipeline
//    NoJavaUtilDate -> nice to have, but we're not that strict
    NoTabCharacter
    ParameterReassignment
//    PublicMethodsBeforeNonPublicMethods -> we're not that strict
//    StaticFieldsBeforeInstanceFields -> we're not that strict
//    StaticMethodsBeforeInstanceMethods -> we're not that strict
    TernaryCouldBeElvis
//    TrailingComma -> breaks pipeline steps
//    VariableTypeRequired -> we're not that strict
    VectorIsObsolete

    // rulesets/exceptions.xml
    // Commented Catch* rules are often caught in pipeline scripts
//    CatchArrayIndexOutOfBoundsException
    CatchError
//    CatchException
//    CatchIllegalMonitorStateException
//    CatchIndexOutOfBoundsException
//    CatchNullPointerException
    CatchRuntimeException
    CatchThrowable
    ConfusingClassNamedException
    ExceptionExtendsError
    ExceptionExtendsThrowable
    ExceptionNotThrown
    MissingNewInThrowStatement
    ReturnNullFromCatchBlock
    SwallowThreadDeath
    ThrowError
    ThrowException
    ThrowNullPointerException
    ThrowRuntimeException
    ThrowThrowable

    // rulesets/formatting.xml
    BlankLineBeforePackage
    BlockEndsWithBlankLine
    BlockStartsWithBlankLine
    BracesForClass
    BracesForForLoop
    BracesForIfElse
    BracesForMethod
    BracesForTryCatchFinally
//    ClassJavadoc -> nice to have, but we're not that strict
    ClosureStatementOnOpeningLineOfMultipleLineClosure
    ConsecutiveBlankLines
    FileEndsWithoutNewline
    Indentation
//    // Use @SuppressWarnings('LineLength') in integration tests with pipeline scripts
//    LineLength { ENABLE
//        length = 100
//        ignoreImportStatements = true
//        ignorePackageStatements = true
//        ignoreLineRegex =  /.*a href.*|.*href.*|.*http:\/\/.*|.*https:\/\/.*|.*ftp:\/\/.*/
//    }
    MissingBlankLineAfterImports
    MissingBlankLineAfterPackage
    SpaceAfterCatch
    SpaceAfterComma
    SpaceAfterClosingBrace
    SpaceAfterFor
    SpaceAfterIf
    SpaceAfterOpeningBrace {
        ignoreEmptyBlock = true
    }
    SpaceAfterSemicolon
    SpaceAfterSwitch
    SpaceAfterWhile
    SpaceAroundClosureArrow
//    SpaceAroundMapEntryColon -> not applicable to pipeline steps
    SpaceAroundOperator
    SpaceBeforeClosingBrace {
        ignoreEmptyBlock = true
    }
    SpaceBeforeOpeningBrace
    TrailingWhitespace
    BlockEndsWithBlankLine

    // rulesets/groovyism.xml
//    AssignCollectionSort -> Rule not valid in Jenkins, sort does not mutate the caller
    AssignCollectionUnique
    ClosureAsLastMethodParameter
    CollectAllIsDeprecated
    ConfusingMultipleReturns
    ExplicitArrayListInstantiation
    ExplicitCallToAndMethod
    ExplicitCallToCompareToMethod
    ExplicitCallToDivMethod
    ExplicitCallToEqualsMethod
    ExplicitCallToGetAtMethod
    ExplicitCallToLeftShiftMethod
    ExplicitCallToMinusMethod
    ExplicitCallToModMethod
    ExplicitCallToMultiplyMethod
    ExplicitCallToOrMethod
    ExplicitCallToPlusMethod
    ExplicitCallToPowerMethod
    ExplicitCallToRightShiftMethod
    ExplicitCallToXorMethod
    ExplicitHashMapInstantiation
    ExplicitHashSetInstantiation
    ExplicitLinkedHashMapInstantiation
    ExplicitLinkedListInstantiation
    ExplicitStackInstantiation
    ExplicitTreeSetInstantiation
    GStringAsMapKey
    GStringExpressionWithinString
    GetterMethodCouldBeProperty
    GroovyLangImmutable
    UseCollectMany
    UseCollectNested

    // rulesets/imports.xml
    DuplicateImport
    ImportFromSamePackage
    ImportFromSunPackages
//    MisorderedStaticImports -> we're not that strict
//    NoWildcardImports -> we're not that strict (useful in tests)
    UnnecessaryGroovyImport
    UnusedImport

    // rulesets/junit.xml
    ChainedTest
    CoupledTestCase
    JUnitAssertAlwaysFails
    JUnitAssertAlwaysSucceeds
    JUnitFailWithoutMessage
    JUnitLostTest
//    JUnitPublicField -> nice to have, but we're not strict in tests
//    JUnitPublicNonTestMethod -> nice to have, but we're not strict in tests
//    JUnitPublicProperty -> nice to have, but we're not strict in tests
    JUnitSetUpCallsSuper
//    JUnitStyleAssertions -> nice to have, but it will be a pain to rewrite every test
    JUnitTearDownCallsSuper
//    JUnitTestMethodWithoutAssert -> integration tests work with logs, no explicit assertions
    JUnitUnnecessarySetUp
    JUnitUnnecessaryTearDown
//    JUnitUnnecessaryThrowsException -> optional + IDE generates throws clause for setUp methods
    SpockIgnoreRestUsed
    UnnecessaryFail
    UseAssertEqualsInsteadOfAssertTrue
    UseAssertFalseInsteadOfNegation
    UseAssertNullInsteadOfAssertEquals
    UseAssertSameInsteadOfAssertTrue
    UseAssertTrueInsteadOfAssertEquals
    UseAssertTrueInsteadOfNegation

    // rulesets/logging.xml
    LoggerForDifferentClass
    LoggerWithWrongModifiers
    LoggingSwallowsStacktrace
    MultipleLoggers
//    PrintStackTrace -> no one overrides System.err default stream in pipelines, so ok
//    Println -> we do allow println in pipelines
    SystemErrPrint
    SystemOutPrint

    // rulesets/naming.xml
    AbstractClassName
    ClassName
    ClassNameSameAsFilename
    ClassNameSameAsSuperclass
    ConfusingMethodName
//    FactoryMethodName -> we do allow this in pipeline (build(), make(), etc.)
    FieldName
//    InterfaceName -> does nothing by default, enable with regex if needed
    InterfaceNameSameAsSuperInterface
    MethodName
    ObjectOverrideMisspelledMethodName
    PackageName
    PackageNameMatchesFilePath
    ParameterName
    PropertyName
    VariableName

    // rulesets/security.xml
    FileCreateTempFile
    InsecureRandom
//    JavaIoPackageAccess -> not applicable
//    NonFinalPublicField -> nice to have, but breaks everything
//    NonFinalSubclassOfSensitiveInterface -> not applicable
//    ObjectFinalize -> not applicable
//    PublicFinalizeMethod -> not applicable
    SystemExit
    UnsafeArrayDeclaration

    // rulesets/serialization.xml
    EnumCustomSerializationIgnored
    SerialPersistentFields
    SerialVersionUID
//    SerializableClassMustDefineSerialVersionUID -> pipeline classes don't require UID

    // rulesets/unnecessary.xml
    AddEmptyString
    ConsecutiveLiteralAppends
    ConsecutiveStringConcatenation
    UnnecessaryBigDecimalInstantiation
    UnnecessaryBigIntegerInstantiation
    UnnecessaryBooleanExpression
    UnnecessaryBooleanInstantiation
    UnnecessaryCallForLastElement
    UnnecessaryCallToSubstring
    UnnecessaryCast
    UnnecessaryCatchBlock
//    UnnecessaryCollectCall -> we're not that strict
    UnnecessaryCollectionCall
    UnnecessaryConstructor
//    UnnecessaryDefInFieldDeclaration -> we're not that strict
//    UnnecessaryDefInMethodDeclaration -> we're not that strict
//    UnnecessaryDefInVariableDeclaration -> we're not that strict
    UnnecessaryDotClass
    UnnecessaryDoubleInstantiation
    UnnecessaryElseStatement
    UnnecessaryFinalOnPrivateMethod
    UnnecessaryFloatInstantiation
//    UnnecessaryGString -> we're not that strict (breaks a lot)
//    UnnecessaryGetter -> will complicate readers' lives
    UnnecessaryIfStatement
    UnnecessaryInstanceOfCheck
    UnnecessaryInstantiationToGetClass
    UnnecessaryIntegerInstantiation
    UnnecessaryLongInstantiation
    UnnecessaryModOne
    UnnecessaryNullCheck
    UnnecessaryNullCheckBeforeInstanceOf
//    UnnecessaryObjectReferences -> we're not that strict
    UnnecessaryOverridingMethod
    UnnecessaryPackageReference
    UnnecessaryParenthesesForMethodCallWithClosure
    UnnecessaryPublicModifier
//    UnnecessaryReturnKeyword -> disabled to make readers' live easier
    UnnecessarySafeNavigationOperator
    UnnecessarySelfAssignment
    UnnecessarySemicolon
//    UnnecessarySetter -> will complicate readers' lives
    UnnecessaryStringInstantiation
//    UnnecessarySubstring -> will complicate readers' lives
    UnnecessaryTernaryExpression
    UnnecessaryToString
    UnnecessaryTransientModifier

    // rulesets/unused.xml
    UnusedArray
    UnusedMethodParameter
    UnusedObject
    UnusedPrivateField
    UnusedPrivateMethod
    UnusedPrivateMethodParameter
//    UnusedVariable ENABLE

    // these shouldn't harm!
    // rulesets/concurrency.xml
    BusyWait
    DoubleCheckedLocking
    InconsistentPropertyLocking
    InconsistentPropertySynchronization
    NestedSynchronization
    StaticCalendarField
    StaticConnection
    StaticDateFormatField
    StaticMatcherField
    StaticSimpleDateFormatField
    SynchronizedMethod
    SynchronizedOnBoxedPrimitive
    SynchronizedOnGetClass
    SynchronizedOnReentrantLock
    SynchronizedOnString
    SynchronizedOnThis
    SynchronizedReadObjectMethod
    SystemRunFinalizersOnExit
    ThisReferenceEscapesConstructor
    ThreadGroup
    ThreadLocalNotStaticFinal
    ThreadYield
    UseOfNotifyMethod
    VolatileArrayField
    VolatileLongOrDoubleField
    WaitOutsideOfWhileLoop

    // rulesets/design.xml
//    AbstractClassWithPublicConstructor ENABLE
    AbstractClassWithoutAbstractMethod
    AssignmentToStaticFieldFromInstanceMethod
    BooleanMethodReturnsNull
//    BuilderMethodWithSideEffects -> breaks prepare methods in tests
    CloneableWithoutClone
    CloseWithoutCloseable
    CompareToWithoutComparable
    ConstantsOnlyInterface
    EmptyMethodInAbstractClass
    FinalClassWithProtectedMember
//    ImplementationAsType ENABLE
//    Instanceof -> nice to have, but we're not that strict
    LocaleSetDefault
//    NestedForLoop -> nice to have, but we're not that strict
//    PrivateFieldCouldBeFinal ENABLE
//    PublicInstanceField -> nice to have, but we're not that strict
    ReturnsNullInsteadOfEmptyArray
    ReturnsNullInsteadOfEmptyCollection
    SimpleDateFormatMissingLocale
    StatelessSingleton
    ToStringReturnsNull

    // nice to have, but we're not that strict in this repo
    // rulesets/size.xml

    // not used
    // rulesets/generic.xml

    // nice to have, but hard to achieve in pipeline scripts
    // rulesets/dry.xml

    // not used
    // rulesets/jdbc.xml

    // not working with pipeline classes (can't compile majority of classes)
    // rulesets/enhanced.xml
}
