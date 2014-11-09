/*
 * Copyright 2013 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codenarc.report

import static org.junit.Assert.assertEquals
import static org.codenarc.test.TestUtil.assertContainsAllInOrder
import static org.codenarc.test.TestUtil.shouldFailWithMessageContaining

import org.codenarc.AnalysisContext
import org.codenarc.results.DirectoryResults
import org.codenarc.results.FileResults
import org.codenarc.rule.Rule
import org.codenarc.rule.StubRule
import org.codenarc.rule.Violation
import org.codenarc.rule.basic.ReturnFromFinallyBlockRule
import org.codenarc.rule.basic.ThrowExceptionFromFinallyBlockRule
import org.codenarc.rule.imports.DuplicateImportRule
import org.codenarc.rule.unnecessary.UnnecessaryBooleanInstantiationRule
import org.codenarc.rule.unnecessary.UnnecessaryStringInstantiationRule
import org.codenarc.ruleset.ListRuleSet
import org.codenarc.test.AbstractTestCase
import org.junit.Before
import org.junit.Test

/**
 * Tests for HtmlReportWriter
 *
 * @author Chris Mair
 */
@SuppressWarnings('LineLength')
class HtmlReportWriterTest extends AbstractTestCase {

    private static final LONG_LINE = 'throw new Exception() // Some very long message 1234567890123456789012345678901234567890'
    private static final MESSAGE = 'bad stuff'
    private static final LINE1 = 111
    private static final LINE2 = 222
    private static final LINE3 = 333
    private static final VIOLATION1 = new Violation(rule:new StubRule(name:'RULE1', priority:1), lineNumber:LINE1, sourceLine:'if (file) {')
    private static final VIOLATION2 = new Violation(rule:new StubRule(name:'RULE2', priority:2), lineNumber:LINE2, message:MESSAGE)
    private static final VIOLATION3 = new Violation(rule:new StubRule(name:'RULE3', priority:3), lineNumber:LINE3, sourceLine:LONG_LINE, message: 'Other info')
    private static final VIOLATION4 = new Violation(rule:new StubRule(name:'RULE4', priority:4), lineNumber:LINE1, sourceLine:'if (file) {')
    private static final NEW_REPORT_FILE = new File('target/NewReport.html').absolutePath
    private static final TITLE = 'My Cool Project'

    private reportWriter
    private analysisContext
    private results
    private dirResultsMain
    private ruleSet
    private String cssFileContents

    @Test
    void testDefaultOutputFile() {
        assert new HtmlReportWriter().defaultOutputFile == HtmlReportWriter.DEFAULT_OUTPUT_FILE
    }

    @Test
    void testWriteReport_DoesNotIncludeRuleDescriptionsForDisabledRules() {
        ruleSet = new ListRuleSet([
                new StubRule(name:'MyRuleXX', enabled:false),
                new StubRule(name:'MyRuleYY'),
                new StubRule(name:'MyRuleZZ', enabled:false)])
        analysisContext.ruleSet = ruleSet
        def reportText = getReportText()
        assert !reportText.contains('MyRuleXX')
        assert !reportText.contains('MyRuleZZ')
    }

    @Test
    void testWriteReport_IncludesRuleThatDoesNotSupportGetDescription() {
        analysisContext.ruleSet = new ListRuleSet([ [getName:{ 'RuleABC' }, getPriority: { 2 } ] as Rule])
        assertContainsAllInOrder(getReportText(), ['RuleABC', 'No description'])
    }

    @Test
    void testWriteReport_NullResults() {
        shouldFailWithMessageContaining('results') { reportWriter.writeReport(analysisContext, null) }
    }

    @Test
    void testWriteReport_NullAnalysisContext() {
        shouldFailWithMessageContaining('analysisContext') { reportWriter.writeReport(null, results) }
    }

    @Test
    void testIsDirectoryContainingFilesWithViolations_FileResults() {
        def results = new FileResults('', [])
        assert !reportWriter.isDirectoryContainingFilesWithViolations(results)

        results = new FileResults('', [VIOLATION1])
        assert !reportWriter.isDirectoryContainingFilesWithViolations(results)
    }

    @Test
    void testIsDirectoryContainingFilesWithViolations_DirectoryResults() {
        def results = new DirectoryResults('')
        assert !reportWriter.isDirectoryContainingFilesWithViolations(results)

        results.addChild(new FileResults('', []))
        assert !reportWriter.isDirectoryContainingFilesWithViolations(results), 'child with no violations'

        def child = new DirectoryResults('')
        child.addChild(new FileResults('', [VIOLATION1]))
        results.addChild(child)
        assert !reportWriter.isDirectoryContainingFilesWithViolations(results), 'grandchild with violations'

        results.addChild(new FileResults('', [VIOLATION2]))
        assert reportWriter.isDirectoryContainingFilesWithViolations(results)

        reportWriter.maxPriority = 1
        assert !reportWriter.isDirectoryContainingFilesWithViolations(results)

        reportWriter.maxPriority = 2
        assert reportWriter.isDirectoryContainingFilesWithViolations(results)

        reportWriter.maxPriority = 1
        results.addChild(new FileResults('', [VIOLATION1]))
        assert reportWriter.isDirectoryContainingFilesWithViolations(results)
    }

    @Test
    void testIsDirectoryContainingFiles() {
        def results = new FileResults('', [])
        assert !reportWriter.isDirectoryContainingFiles(results)

        results = new DirectoryResults('')
        assert !reportWriter.isDirectoryContainingFiles(results)

        results.numberOfFilesInThisDirectory = 2
        assert reportWriter.isDirectoryContainingFiles(results)
    }

    @Test
    void testFormatSourceLine() {
        assert reportWriter.formatSourceLine('') == null
        assert reportWriter.formatSourceLine('abc') == 'abc'
        assert reportWriter.formatSourceLine('abcdef' * 20) == 'abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefab..abcdefabcdef'
        assert reportWriter.formatSourceLine('abc', 2) == 'abc'
        assert reportWriter.formatSourceLine('abcdef' * 20, 2) == 'cdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd..abcdefabcdef'
    }

    @Test
    void testMaxPriority_DefaultsTo3() {
        assert reportWriter.maxPriority == 3
    }

    @Test
    void testWriteReport() {
        final EXPECTED = """
            <html><head><title>CodeNarc Report</title><style type='text/css'>$cssFileContents</style></head><body><img class='logo' src='http://codenarc.sourceforge.net/images/codenarc-logo.png' alt='CodeNarc' align='right'/><h1>CodeNarc Report</h1><div class='metadata'><table><tr><td class='em'>Report title:</td><td/></tr><tr><td class='em'>Date:</td><td>Feb 24, 2011 9:32:38 PM</td></tr><tr><td class='em'>Generated with:</td><td><a href='http://www.codenarc.org'>CodeNarc v0.12</a></td></tr></table></div><div class='summary'><h2>Summary by Package</h2><table><tr class='tableHeader'><th>Package</th><th>Total Files</th><th>Files with Violations</th><th>Priority 1</th><th>Priority 2</th><th>Priority 3</th></tr><tr><td class='allPackages'>All Packages</td><td class='number'>10</td><td class='number'>3</td><td class='priority1'>3</td><td class='priority2'>2</td><td class='priority3'>3</td></tr><tr><td><a href='#src/main'>src/main</a></td><td class='number'>1</td><td class='number'>1</td><td class='priority1'>2</td><td class='priority2'>1</td><td class='priority3'>2</td></tr><tr><td><a href='#src/main/code'>src/main/code</a></td><td class='number'>2</td><td class='number'>1</td><td class='priority1'>-</td><td class='priority2'>-</td><td class='priority3'>1</td></tr><tr><td><a href='#src/main/test'>src/main/test</a></td><td class='number'>3</td><td class='number'>1</td><td class='priority1'>1</td><td class='priority2'>1</td><td class='priority3'>-</td></tr><tr><td>src/main/test/noviolations</td><td class='number'>4</td><td class='number'>-</td><td class='priority1'>-</td><td class='priority2'>-</td><td class='priority3'>-</td></tr></table></div><div class='summary'><a name='src/main'> </a><h2 class='packageHeader'>Package: src.main</h2></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;MyAction.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr><tr><td><a href='#RULE2'>RULE2</a></td><td class='priority2'>2</td><td class='number'>222</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>bad stuff</span></p></td></tr><tr><td><a href='#RULE3'>RULE3</a></td><td class='priority3'>3</td><td class='number'>333</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>throw new Exception() // Some very long message 12345678..901234567890</span></p><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>Other info</span></p></td></tr><tr><td><a href='#RULE3'>RULE3</a></td><td class='priority3'>3</td><td class='number'>333</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>throw new Exception() // Some very long message 12345678..901234567890</span></p><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>Other info</span></p></td></tr></table></div><div class='summary'><a name='src/main/code'> </a><h2 class='packageHeader'>Package: src.main.code</h2></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;src/main/MyAction2.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE3'>RULE3</a></td><td class='priority3'>3</td><td class='number'>333</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>throw new Exception() // Some very long message 12345678..901234567890</span></p><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>Other info</span></p></td></tr></table></div><div class='summary'><a name='src/main/test'> </a><h2 class='packageHeader'>Package: src.main.test</h2></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;src/main/MyActionTest.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr><tr><td><a href='#RULE2'>RULE2</a></td><td class='priority2'>2</td><td class='number'>222</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>bad stuff</span></p></td></tr></table></div><div class='summary'><h2>Rule Descriptions</h2><table border='1'><tr class='tableHeader'><th class='ruleDescriptions'>#</th><th class='ruleDescriptions'>Rule Name</th><th class='ruleDescriptions'>Description</th></tr><tr class='ruleDescriptions'><td><a name='DuplicateImport'></a><span class='ruleIndex'>1</span></td><td class='ruleName priority3'>DuplicateImport</td><td>Duplicate import statements are unnecessary.</td></tr><tr class='ruleDescriptions'><td><a name='ReturnFromFinallyBlock'></a><span class='ruleIndex'>2</span></td><td class='ruleName priority2'>ReturnFromFinallyBlock</td><td>Returning from a <em>finally</em> block is confusing and can hide the original exception.</td></tr><tr class='ruleDescriptions'><td><a name='ThrowExceptionFromFinallyBlock'></a><span class='ruleIndex'>3</span></td><td class='ruleName priority2'>ThrowExceptionFromFinallyBlock</td><td>Throwing an exception from a <em>finally</em> block is confusing and can hide the original exception.</td></tr><tr class='ruleDescriptions'><td><a name='UnnecessaryBooleanInstantiation'></a><span class='ruleIndex'>4</span></td><td class='ruleName priority3'>UnnecessaryBooleanInstantiation</td><td>Use <em>Boolean.valueOf()</em> for variable values or <em>Boolean.TRUE</em> and <em>Boolean.FALSE</em> for constant values instead of calling the <em>Boolean()</em> constructor directly or calling <em>Boolean.valueOf(true)</em> or <em>Boolean.valueOf(false)</em>.</td></tr><tr class='ruleDescriptions'><td><a name='UnnecessaryStringInstantiation'></a><span class='ruleIndex'>5</span></td><td class='ruleName priority3'>UnnecessaryStringInstantiation</td><td>Use a String literal (e.g., "...") instead of calling the corresponding String constructor (new String("..")) directly.</td></tr></table></div></body></html>
            """
        reportWriter.writeReport(analysisContext, results)
        assertReportFileContents(NEW_REPORT_FILE, EXPECTED)
    }

    @Test
    void testWriteReport_SetTitle() {
        final EXPECTED = """
            <html><head><title>CodeNarc Report: $TITLE</title><style type='text/css'>$cssFileContents</style></head><body><img class='logo' src='http://codenarc.sourceforge.net/images/codenarc-logo.png' alt='CodeNarc' align='right'/><h1>CodeNarc Report</h1><div class='metadata'><table><tr><td class='em'>Report title:</td><td>$TITLE</td></tr><tr><td class='em'>Date:</td><td>Feb 24, 2011 9:32:38 PM</td></tr><tr><td class='em'>Generated with:</td><td><a href='http://www.codenarc.org'>CodeNarc v0.12</a></td></tr></table></div><div class='summary'><h2>Summary by Package</h2><table><tr class='tableHeader'><th>Package</th><th>Total Files</th><th>Files with Violations</th><th>Priority 1</th><th>Priority 2</th><th>Priority 3</th></tr><tr><td class='allPackages'>All Packages</td><td class='number'>10</td><td class='number'>3</td><td class='priority1'>3</td><td class='priority2'>2</td><td class='priority3'>3</td></tr><tr><td><a href='#src/main'>src/main</a></td><td class='number'>1</td><td class='number'>1</td><td class='priority1'>2</td><td class='priority2'>1</td><td class='priority3'>2</td></tr><tr><td><a href='#src/main/code'>src/main/code</a></td><td class='number'>2</td><td class='number'>1</td><td class='priority1'>-</td><td class='priority2'>-</td><td class='priority3'>1</td></tr><tr><td><a href='#src/main/test'>src/main/test</a></td><td class='number'>3</td><td class='number'>1</td><td class='priority1'>1</td><td class='priority2'>1</td><td class='priority3'>-</td></tr><tr><td>src/main/test/noviolations</td><td class='number'>4</td><td class='number'>-</td><td class='priority1'>-</td><td class='priority2'>-</td><td class='priority3'>-</td></tr></table></div><div class='summary'><a name='src/main'> </a><h2 class='packageHeader'>Package: src.main</h2></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;MyAction.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr><tr><td><a href='#RULE2'>RULE2</a></td><td class='priority2'>2</td><td class='number'>222</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>bad stuff</span></p></td></tr><tr><td><a href='#RULE3'>RULE3</a></td><td class='priority3'>3</td><td class='number'>333</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>throw new Exception() // Some very long message 12345678..901234567890</span></p><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>Other info</span></p></td></tr><tr><td><a href='#RULE3'>RULE3</a></td><td class='priority3'>3</td><td class='number'>333</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>throw new Exception() // Some very long message 12345678..901234567890</span></p><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>Other info</span></p></td></tr></table></div><div class='summary'><a name='src/main/code'> </a><h2 class='packageHeader'>Package: src.main.code</h2></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;src/main/MyAction2.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE3'>RULE3</a></td><td class='priority3'>3</td><td class='number'>333</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>throw new Exception() // Some very long message 12345678..901234567890</span></p><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>Other info</span></p></td></tr></table></div><div class='summary'><a name='src/main/test'> </a><h2 class='packageHeader'>Package: src.main.test</h2></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;src/main/MyActionTest.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr><tr><td><a href='#RULE2'>RULE2</a></td><td class='priority2'>2</td><td class='number'>222</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>bad stuff</span></p></td></tr></table></div><div class='summary'><h2>Rule Descriptions</h2><table border='1'><tr class='tableHeader'><th class='ruleDescriptions'>#</th><th class='ruleDescriptions'>Rule Name</th><th class='ruleDescriptions'>Description</th></tr><tr class='ruleDescriptions'><td><a name='DuplicateImport'></a><span class='ruleIndex'>1</span></td><td class='ruleName priority3'>DuplicateImport</td><td>Duplicate import statements are unnecessary.</td></tr><tr class='ruleDescriptions'><td><a name='ReturnFromFinallyBlock'></a><span class='ruleIndex'>2</span></td><td class='ruleName priority2'>ReturnFromFinallyBlock</td><td>Returning from a <em>finally</em> block is confusing and can hide the original exception.</td></tr><tr class='ruleDescriptions'><td><a name='ThrowExceptionFromFinallyBlock'></a><span class='ruleIndex'>3</span></td><td class='ruleName priority2'>ThrowExceptionFromFinallyBlock</td><td>Throwing an exception from a <em>finally</em> block is confusing and can hide the original exception.</td></tr><tr class='ruleDescriptions'><td><a name='UnnecessaryBooleanInstantiation'></a><span class='ruleIndex'>4</span></td><td class='ruleName priority3'>UnnecessaryBooleanInstantiation</td><td>Use <em>Boolean.valueOf()</em> for variable values or <em>Boolean.TRUE</em> and <em>Boolean.FALSE</em> for constant values instead of calling the <em>Boolean()</em> constructor directly or calling <em>Boolean.valueOf(true)</em> or <em>Boolean.valueOf(false)</em>.</td></tr><tr class='ruleDescriptions'><td><a name='UnnecessaryStringInstantiation'></a><span class='ruleIndex'>5</span></td><td class='ruleName priority3'>UnnecessaryStringInstantiation</td><td>Use a String literal (e.g., "...") instead of calling the corresponding String constructor (new String("..")) directly.</td></tr></table></div></body></html>
            """
        reportWriter.title = TITLE
        reportWriter.writeReport(analysisContext, results)
        assertReportFileContents(NEW_REPORT_FILE, EXPECTED)
    }

    @Test
    void testWriteReport_MaxPriority1() {
        final EXPECTED = """
            <html><head><title>CodeNarc Report</title><style type='text/css'>$cssFileContents</style></head><body><img class='logo' src='http://codenarc.sourceforge.net/images/codenarc-logo.png' alt='CodeNarc' align='right'/><h1>CodeNarc Report</h1><div class='metadata'><table><tr><td class='em'>Report title:</td><td/></tr><tr><td class='em'>Date:</td><td>Feb 24, 2011 9:32:38 PM</td></tr><tr><td class='em'>Generated with:</td><td><a href='http://www.codenarc.org'>CodeNarc v0.12</a></td></tr></table></div><div class='summary'><h2>Summary by Package</h2><table><tr class='tableHeader'><th>Package</th><th>Total Files</th><th>Files with Violations</th><th>Priority 1</th></tr><tr><td class='allPackages'>All Packages</td><td class='number'>10</td><td class='number'>2</td><td class='priority1'>3</td></tr><tr><td><a href='#src/main'>src/main</a></td><td class='number'>1</td><td class='number'>1</td><td class='priority1'>2</td></tr><tr><td>src/main/code</td><td class='number'>2</td><td class='number'>-</td><td class='priority1'>-</td></tr><tr><td><a href='#src/main/test'>src/main/test</a></td><td class='number'>3</td><td class='number'>1</td><td class='priority1'>1</td></tr><tr><td>src/main/test/noviolations</td><td class='number'>4</td><td class='number'>-</td><td class='priority1'>-</td></tr></table></div><div class='summary'><a name='src/main'></a><h2 class='packageHeader'>Package: src.main</h2></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;MyAction.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr></table></div><div class='summary'><a name='src/main/test'></a><h2 class='packageHeader'>Package: src.main.test</h2></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;src/main/MyActionTest.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr></table></div><div class='summary'><h2>Rule Descriptions</h2><table border='1'><tr class='tableHeader'><th class='ruleDescriptions'>#</th><th class='ruleDescriptions'>Rule Name</th><th class='ruleDescriptions'>Description</th></tr><tr class='ruleDescriptions'><td><a name='DuplicateImport'></a><span class='ruleIndex'>1</span></td><td class='ruleName priority3'>DuplicateImport</td><td>Duplicate import statements are unnecessary.</td></tr><tr class='ruleDescriptions'><td><a name='ReturnFromFinallyBlock'></a><span class='ruleIndex'>2</span></td><td class='ruleName priority2'>ReturnFromFinallyBlock</td><td>Returning from a <em>finally</em> block is confusing and can hide the original exception.</td></tr><tr class='ruleDescriptions'><td><a name='ThrowExceptionFromFinallyBlock'></a><span class='ruleIndex'>3</span></td><td class='ruleName priority2'>ThrowExceptionFromFinallyBlock</td><td>Throwing an exception from a <em>finally</em> block is confusing and can hide the original exception.</td></tr><tr class='ruleDescriptions'><td><a name='UnnecessaryBooleanInstantiation'></a><span class='ruleIndex'>4</span></td><td class='ruleName priority3'>UnnecessaryBooleanInstantiation</td><td>Use <em>Boolean.valueOf()</em> for variable values or <em>Boolean.TRUE</em> and <em>Boolean.FALSE</em> for constant values instead of calling the <em>Boolean()</em> constructor directly or calling <em>Boolean.valueOf(true)</em> or <em>Boolean.valueOf(false)</em>.</td></tr><tr class='ruleDescriptions'><td><a name='UnnecessaryStringInstantiation'></a><span class='ruleIndex'>5</span></td><td class='ruleName priority3'>UnnecessaryStringInstantiation</td><td>Use a String literal (e.g., "...") instead of calling the corresponding String constructor (new String("..")) directly.</td></tr></table></div></body></html>
            """
        reportWriter.maxPriority = 1
        assertReportContents(EXPECTED)
    }

    @Test
    void testWriteReport_Priority4() {
        final EXPECTED = """
            <html><head><title>CodeNarc Report</title><style type='text/css'>$cssFileContents</style></head><body><img class='logo' src='http://codenarc.sourceforge.net/images/codenarc-logo.png' alt='CodeNarc' align='right'/><h1>CodeNarc Report</h1><div class='metadata'><table><tr><td class='em'>Report title:</td><td/></tr><tr><td class='em'>Date:</td><td>Feb 24, 2011 9:32:38 PM</td></tr><tr><td class='em'>Generated with:</td><td><a href='http://www.codenarc.org'>CodeNarc v0.12</a></td></tr></table></div><div class='summary'><h2>Summary by Package</h2><table><tr class='tableHeader'><th>Package</th><th>Total Files</th><th>Files with Violations</th><th>Priority 1</th><th>Priority 2</th><th>Priority 3</th><th>Priority 4</th></tr><tr><td class='allPackages'>All Packages</td><td class='number'>10</td><td class='number'>4</td><td class='priority1'>3</td><td class='priority2'>2</td><td class='priority3'>3</td><td class='priority4'>1</td></tr><tr><td><a href='#src/main'>src/main</a></td><td class='number'>1</td><td class='number'>2</td><td class='priority1'>2</td><td class='priority2'>1</td><td class='priority3'>2</td><td class='priority4'>1</td></tr><tr><td><a href='#src/main/code'>src/main/code</a></td><td class='number'>2</td><td class='number'>1</td><td class='priority1'>-</td><td class='priority2'>-</td><td class='priority3'>1</td><td class='priority4'>-</td></tr><tr><td><a href='#src/main/test'>src/main/test</a></td><td class='number'>3</td><td class='number'>1</td><td class='priority1'>1</td><td class='priority2'>1</td><td class='priority3'>-</td><td class='priority4'>-</td></tr><tr><td>src/main/test/noviolations</td><td class='number'>4</td><td class='number'>-</td><td class='priority1'>-</td><td class='priority2'>-</td><td class='priority3'>-</td><td class='priority4'>-</td></tr></table></div><div class='summary'><a name='src/main'></a><h2 class='packageHeader'>Package: src.main</h2></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;MyAction.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr><tr><td><a href='#RULE2'>RULE2</a></td><td class='priority2'>2</td><td class='number'>222</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>bad stuff</span></p></td></tr><tr><td><a href='#RULE3'>RULE3</a></td><td class='priority3'>3</td><td class='number'>333</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>throw new Exception() // Some very long message 12345678..901234567890</span></p><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>Other info</span></p></td></tr><tr><td><a href='#RULE3'>RULE3</a></td><td class='priority3'>3</td><td class='number'>333</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>throw new Exception() // Some very long message 12345678..901234567890</span></p><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>Other info</span></p></td></tr></table></div><div class='summary'><a name='src/main/code'></a><h2 class='packageHeader'>Package: src.main.code</h2></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;src/main/MyAction2.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE3'>RULE3</a></td><td class='priority3'>3</td><td class='number'>333</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>throw new Exception() // Some very long message 12345678..901234567890</span></p><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>Other info</span></p></td></tr></table></div><div class='summary'><a name='src/main/test'></a><h2 class='packageHeader'>Package: src.main.test</h2></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;src/main/MyActionTest.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr><tr><td><a href='#RULE2'>RULE2</a></td><td class='priority2'>2</td><td class='number'>222</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>bad stuff</span></p></td></tr></table></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;MyActionTest.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE4'>RULE4</a></td><td class='priority4'>4</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr></table></div><div class='summary'><h2>Rule Descriptions</h2><table border='1'><tr class='tableHeader'><th class='ruleDescriptions'>#</th><th class='ruleDescriptions'>Rule Name</th><th class='ruleDescriptions'>Description</th></tr><tr class='ruleDescriptions'><td><a name='DuplicateImport'></a><span class='ruleIndex'>1</span></td><td class='ruleName priority3'>DuplicateImport</td><td>Duplicate import statements are unnecessary.</td></tr><tr class='ruleDescriptions'><td><a name='ReturnFromFinallyBlock'></a><span class='ruleIndex'>2</span></td><td class='ruleName priority2'>ReturnFromFinallyBlock</td><td>Returning from a <em>finally</em> block is confusing and can hide the original exception.</td></tr><tr class='ruleDescriptions'><td><a name='ThrowExceptionFromFinallyBlock'></a><span class='ruleIndex'>3</span></td><td class='ruleName priority2'>ThrowExceptionFromFinallyBlock</td><td>Throwing an exception from a <em>finally</em> block is confusing and can hide the original exception.</td></tr><tr class='ruleDescriptions'><td><a name='UnnecessaryBooleanInstantiation'></a><span class='ruleIndex'>4</span></td><td class='ruleName priority3'>UnnecessaryBooleanInstantiation</td><td>Use <em>Boolean.valueOf()</em> for variable values or <em>Boolean.TRUE</em> and <em>Boolean.FALSE</em> for constant values instead of calling the <em>Boolean()</em> constructor directly or calling <em>Boolean.valueOf(true)</em> or <em>Boolean.valueOf(false)</em>.</td></tr><tr class='ruleDescriptions'><td><a name='UnnecessaryStringInstantiation'></a><span class='ruleIndex'>5</span></td><td class='ruleName priority3'>UnnecessaryStringInstantiation</td><td>Use a String literal (e.g., "...") instead of calling the corresponding String constructor (new String("..")) directly.</td></tr></table></div></body></html>
            """
        def fileResults4 = new FileResults('src/main/MyActionTest.groovy', [VIOLATION4])
        dirResultsMain.addChild(fileResults4)
        reportWriter.maxPriority = 4
        assertReportContents(EXPECTED)
    }

    @Test
    void testWriteReport_NoDescriptionsForRuleIds() {
        final EXPECTED = """
            <html><head><title>CodeNarc Report</title><style type='text/css'>$cssFileContents</style></head><body><img class='logo' src='http://codenarc.sourceforge.net/images/codenarc-logo.png' alt='CodeNarc' align='right'/><h1>CodeNarc Report</h1><div class='metadata'><table><tr><td class='em'>Report title:</td><td/></tr><tr><td class='em'>Date:</td><td>Feb 24, 2011 9:32:38 PM</td></tr><tr><td class='em'>Generated with:</td><td><a href='http://www.codenarc.org'>CodeNarc v0.12</a></td></tr></table></div><div class='summary'><h2>Summary by Package</h2><table><tr class='tableHeader'><th>Package</th><th>Total Files</th><th>Files with Violations</th><th>Priority 1</th><th>Priority 2</th><th>Priority 3</th></tr><tr><td class='allPackages'>All Packages</td><td class='number'>10</td><td class='number'>3</td><td class='priority1'>3</td><td class='priority2'>2</td><td class='priority3'>3</td></tr><tr><td><a href='#src/main'>src/main</a></td><td class='number'>1</td><td class='number'>1</td><td class='priority1'>2</td><td class='priority2'>1</td><td class='priority3'>2</td></tr><tr><td><a href='#src/main/code'>src/main/code</a></td><td class='number'>2</td><td class='number'>1</td><td class='priority1'>-</td><td class='priority2'>-</td><td class='priority3'>1</td></tr><tr><td><a href='#src/main/test'>src/main/test</a></td><td class='number'>3</td><td class='number'>1</td><td class='priority1'>1</td><td class='priority2'>1</td><td class='priority3'>-</td></tr><tr><td>src/main/test/noviolations</td><td class='number'>4</td><td class='number'>-</td><td class='priority1'>-</td><td class='priority2'>-</td><td class='priority3'>-</td></tr></table></div><div class='summary'><a name='src/main'></a><h2 class='packageHeader'>Package: src.main</h2></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;MyAction.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr><tr><td><a href='#RULE2'>RULE2</a></td><td class='priority2'>2</td><td class='number'>222</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>bad stuff</span></p></td></tr><tr><td><a href='#RULE3'>RULE3</a></td><td class='priority3'>3</td><td class='number'>333</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>throw new Exception() // Some very long message 12345678..901234567890</span></p><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>Other info</span></p></td></tr><tr><td><a href='#RULE3'>RULE3</a></td><td class='priority3'>3</td><td class='number'>333</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>throw new Exception() // Some very long message 12345678..901234567890</span></p><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>Other info</span></p></td></tr></table></div><div class='summary'><a name='src/main/code'></a><h2 class='packageHeader'>Package: src.main.code</h2></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;src/main/MyAction2.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE3'>RULE3</a></td><td class='priority3'>3</td><td class='number'>333</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>throw new Exception() // Some very long message 12345678..901234567890</span></p><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>Other info</span></p></td></tr></table></div><div class='summary'><a name='src/main/test'></a><h2 class='packageHeader'>Package: src.main.test</h2></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;src/main/MyActionTest.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr><tr><td><a href='#RULE2'>RULE2</a></td><td class='priority2'>2</td><td class='number'>222</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>bad stuff</span></p></td></tr></table></div><div class='summary'><h2>Rule Descriptions</h2><table border='1'><tr class='tableHeader'><th class='ruleDescriptions'>#</th><th class='ruleDescriptions'>Rule Name</th><th class='ruleDescriptions'>Description</th></tr><tr class='ruleDescriptions'><td><a name='MyRuleXX'></a><span class='ruleIndex'>1</span></td><td class='ruleName priority0'>MyRuleXX</td><td>No description provided for rule named [MyRuleXX]</td></tr><tr class='ruleDescriptions'><td><a name='MyRuleYY'></a><span class='ruleIndex'>2</span></td><td class='ruleName priority0'>MyRuleYY</td><td>No description provided for rule named [MyRuleYY]</td></tr></table></div></body></html>
            """
        ruleSet = new ListRuleSet([new StubRule(name:'MyRuleXX'), new StubRule(name:'MyRuleYY')])
        reportWriter.customMessagesBundleName = 'DoesNotExist'
        analysisContext.ruleSet = ruleSet
        assertReportContents(EXPECTED)
    }

    @Test
    void testWriteReport_RuleDescriptionsProvidedInCodeNarcMessagesFile() {
        final EXPECTED = """
            <html><head><title>CodeNarc Report</title><style type='text/css'>$cssFileContents</style></head><body><img class='logo' src='http://codenarc.sourceforge.net/images/codenarc-logo.png' alt='CodeNarc' align='right'/><h1>CodeNarc Report</h1><div class='metadata'><table><tr><td class='em'>Report title:</td><td/></tr><tr><td class='em'>Date:</td><td>Feb 24, 2011 9:32:38 PM</td></tr><tr><td class='em'>Generated with:</td><td><a href='http://www.codenarc.org'>CodeNarc v0.12</a></td></tr></table></div><div class='summary'><h2>Summary by Package</h2><table><tr class='tableHeader'><th>Package</th><th>Total Files</th><th>Files with Violations</th><th>Priority 1</th><th>Priority 2</th><th>Priority 3</th></tr><tr><td class='allPackages'>All Packages</td><td class='number'>10</td><td class='number'>3</td><td class='priority1'>3</td><td class='priority2'>2</td><td class='priority3'>3</td></tr><tr><td><a href='#src/main'>src/main</a></td><td class='number'>1</td><td class='number'>1</td><td class='priority1'>2</td><td class='priority2'>1</td><td class='priority3'>2</td></tr><tr><td><a href='#src/main/code'>src/main/code</a></td><td class='number'>2</td><td class='number'>1</td><td class='priority1'>-</td><td class='priority2'>-</td><td class='priority3'>1</td></tr><tr><td><a href='#src/main/test'>src/main/test</a></td><td class='number'>3</td><td class='number'>1</td><td class='priority1'>1</td><td class='priority2'>1</td><td class='priority3'>-</td></tr><tr><td>src/main/test/noviolations</td><td class='number'>4</td><td class='number'>-</td><td class='priority1'>-</td><td class='priority2'>-</td><td class='priority3'>-</td></tr></table></div><div class='summary'><a name='src/main'></a><h2 class='packageHeader'>Package: src.main</h2></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;MyAction.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr><tr><td><a href='#RULE2'>RULE2</a></td><td class='priority2'>2</td><td class='number'>222</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>bad stuff</span></p></td></tr><tr><td><a href='#RULE3'>RULE3</a></td><td class='priority3'>3</td><td class='number'>333</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>throw new Exception() // Some very long message 12345678..901234567890</span></p><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>Other info</span></p></td></tr><tr><td><a href='#RULE3'>RULE3</a></td><td class='priority3'>3</td><td class='number'>333</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>throw new Exception() // Some very long message 12345678..901234567890</span></p><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>Other info</span></p></td></tr></table></div><div class='summary'><a name='src/main/code'></a><h2 class='packageHeader'>Package: src.main.code</h2></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;src/main/MyAction2.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE3'>RULE3</a></td><td class='priority3'>3</td><td class='number'>333</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>throw new Exception() // Some very long message 12345678..901234567890</span></p><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>Other info</span></p></td></tr></table></div><div class='summary'><a name='src/main/test'></a><h2 class='packageHeader'>Package: src.main.test</h2></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;src/main/MyActionTest.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr><tr><td><a href='#RULE2'>RULE2</a></td><td class='priority2'>2</td><td class='number'>222</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>bad stuff</span></p></td></tr></table></div><div class='summary'><h2>Rule Descriptions</h2><table border='1'><tr class='tableHeader'><th class='ruleDescriptions'>#</th><th class='ruleDescriptions'>Rule Name</th><th class='ruleDescriptions'>Description</th></tr><tr class='ruleDescriptions'><td><a name='MyRuleXX'></a><span class='ruleIndex'>1</span></td><td class='ruleName priority0'>MyRuleXX</td><td>HTML Rule XX</td></tr><tr class='ruleDescriptions'><td><a name='MyRuleYY'></a><span class='ruleIndex'>2</span></td><td class='ruleName priority0'>MyRuleYY</td><td>My Rule YY</td></tr><tr class='ruleDescriptions'><td><a name='UnnecessaryBooleanInstantiation'></a><span class='ruleIndex'>3</span></td><td class='ruleName priority3'>UnnecessaryBooleanInstantiation</td><td>Use <em>Boolean.valueOf()</em> for variable values or <em>Boolean.TRUE</em> and <em>Boolean.FALSE</em> for constant values instead of calling the <em>Boolean()</em> constructor directly or calling <em>Boolean.valueOf(true)</em> or <em>Boolean.valueOf(false)</em>.</td></tr></table></div></body></html>
            """
        def biRule = new UnnecessaryBooleanInstantiationRule()
        ruleSet = new ListRuleSet([new StubRule(name:'MyRuleXX'), new StubRule(name:'MyRuleYY'), biRule])
        analysisContext.ruleSet = ruleSet
        assertReportContents(EXPECTED)
    }

    @Test
    void testWriteReport_RuleDescriptionsSetDirectlyOnTheRule() {
        final EXPECTED = """
            <html><head><title>CodeNarc Report</title><style type='text/css'>$cssFileContents</style></head><body><img class='logo' src='http://codenarc.sourceforge.net/images/codenarc-logo.png' alt='CodeNarc' align='right'/><h1>CodeNarc Report</h1><div class='metadata'><table><tr><td class='em'>Report title:</td><td/></tr><tr><td class='em'>Date:</td><td>Feb 24, 2011 9:32:38 PM</td></tr><tr><td class='em'>Generated with:</td><td><a href='http://www.codenarc.org'>CodeNarc v0.12</a></td></tr></table></div><div class='summary'><h2>Summary by Package</h2><table><tr class='tableHeader'><th>Package</th><th>Total Files</th><th>Files with Violations</th><th>Priority 1</th><th>Priority 2</th><th>Priority 3</th></tr><tr><td class='allPackages'>All Packages</td><td class='number'>10</td><td class='number'>3</td><td class='priority1'>3</td><td class='priority2'>2</td><td class='priority3'>3</td></tr><tr><td><a href='#src/main'>src/main</a></td><td class='number'>1</td><td class='number'>1</td><td class='priority1'>2</td><td class='priority2'>1</td><td class='priority3'>2</td></tr><tr><td><a href='#src/main/code'>src/main/code</a></td><td class='number'>2</td><td class='number'>1</td><td class='priority1'>-</td><td class='priority2'>-</td><td class='priority3'>1</td></tr><tr><td><a href='#src/main/test'>src/main/test</a></td><td class='number'>3</td><td class='number'>1</td><td class='priority1'>1</td><td class='priority2'>1</td><td class='priority3'>-</td></tr><tr><td>src/main/test/noviolations</td><td class='number'>4</td><td class='number'>-</td><td class='priority1'>-</td><td class='priority2'>-</td><td class='priority3'>-</td></tr></table></div><div class='summary'><a name='src/main'></a><h2 class='packageHeader'>Package: src.main</h2></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;MyAction.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr><tr><td><a href='#RULE2'>RULE2</a></td><td class='priority2'>2</td><td class='number'>222</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>bad stuff</span></p></td></tr><tr><td><a href='#RULE3'>RULE3</a></td><td class='priority3'>3</td><td class='number'>333</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>throw new Exception() // Some very long message 12345678..901234567890</span></p><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>Other info</span></p></td></tr><tr><td><a href='#RULE3'>RULE3</a></td><td class='priority3'>3</td><td class='number'>333</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>throw new Exception() // Some very long message 12345678..901234567890</span></p><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>Other info</span></p></td></tr></table></div><div class='summary'><a name='src/main/code'></a><h2 class='packageHeader'>Package: src.main.code</h2></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;src/main/MyAction2.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE3'>RULE3</a></td><td class='priority3'>3</td><td class='number'>333</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>throw new Exception() // Some very long message 12345678..901234567890</span></p><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>Other info</span></p></td></tr></table></div><div class='summary'><a name='src/main/test'></a><h2 class='packageHeader'>Package: src.main.test</h2></div><div class='summary'><h3 class='fileHeader'>&#x27A5;&nbsp;src/main/MyActionTest.groovy</h3><table border='1'><tr class='tableHeader'><th>Rule Name</th><th>Priority</th><th>Line #</th><th>Source Line / Message</th></tr><tr><td><a href='#RULE1'>RULE1</a></td><td class='priority1'>1</td><td class='number'>111</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[SRC]</span><span class='sourceCode'>if (file) {</span></p></td></tr><tr><td><a href='#RULE2'>RULE2</a></td><td class='priority2'>2</td><td class='number'>222</td><td><p class='violationInfo'><span class='violationInfoPrefix'>[MSG]</span><span class='violationMessage'>bad stuff</span></p></td></tr></table></div><div class='summary'><h2>Rule Descriptions</h2><table border='1'><tr class='tableHeader'><th class='ruleDescriptions'>#</th><th class='ruleDescriptions'>Rule Name</th><th class='ruleDescriptions'>Description</th></tr><tr class='ruleDescriptions'><td><a name='MyRuleXX'></a><span class='ruleIndex'>1</span></td><td class='ruleName priority0'>MyRuleXX</td><td>description77</td></tr><tr class='ruleDescriptions'><td><a name='MyRuleYY'></a><span class='ruleIndex'>2</span></td><td class='ruleName priority0'>MyRuleYY</td><td>description88</td></tr></table></div></body></html>
            """
        ruleSet = new ListRuleSet([
            new StubRule(name:'MyRuleXX', description:'description77'),
            new StubRule(name:'MyRuleYY', description:'description88')])
        analysisContext.ruleSet = ruleSet
        assertReportContents(EXPECTED)
    }

    //------------------------------------------------------------------------------------
    // Setup and tear-down and helper methods
    //------------------------------------------------------------------------------------

    @Before
    void setUpHtmlReportWriterTest() {
        log(new File('.').absolutePath)

        reportWriter = new HtmlReportWriter(outputFile:NEW_REPORT_FILE)
        reportWriter.metaClass.getFormattedTimestamp << { 'Feb 24, 2011 9:32:38 PM' }
        reportWriter.metaClass.getCodeNarcVersion << { '0.12' }

        dirResultsMain = new DirectoryResults('src/main', 1)
        def dirResultsCode = new DirectoryResults('src/main/code', 2)
        def dirResultsTest = new DirectoryResults('src/main/test', 3)
        def dirResultsTestSubdirNoViolations = new DirectoryResults('src/main/test/noviolations', 4)
        def dirResultsTestSubdirEmpty = new DirectoryResults('src/main/test/empty')
        def fileResults1 = new FileResults('src/main/MyAction.groovy', [VIOLATION1, VIOLATION3, VIOLATION3, VIOLATION1, VIOLATION2])
        def fileResults2 = new FileResults('src/main/MyAction2.groovy', [VIOLATION3])
        def fileResults3 = new FileResults('src/main/MyActionTest.groovy', [VIOLATION1, VIOLATION2])
        dirResultsMain.addChild(fileResults1)
        dirResultsMain.addChild(dirResultsCode)
        dirResultsMain.addChild(dirResultsTest)
        dirResultsCode.addChild(fileResults2)
        dirResultsTest.addChild(fileResults3)
        dirResultsTest.addChild(dirResultsTestSubdirNoViolations)
        dirResultsTest.addChild(dirResultsTestSubdirEmpty)
        results = new DirectoryResults()
        results.addChild(dirResultsMain)

        ruleSet = new ListRuleSet([
                new UnnecessaryBooleanInstantiationRule(),
                new ReturnFromFinallyBlockRule(),
                new UnnecessaryStringInstantiationRule(),
                new ThrowExceptionFromFinallyBlockRule(),
                new DuplicateImportRule()
        ])
        analysisContext = new AnalysisContext(sourceDirectories:['/src/main'], ruleSet:ruleSet)

        cssFileContents = new File('src/main/resources/codenarc-htmlreport.css').text
    }

    private String getReportText() {
        def writer = new StringWriter()
        reportWriter.writeReport(writer, analysisContext, results)
        return writer.toString()
    }

    private void assertReportContents(String expected) {
        String actual = getReportText()
        assertEquals(normalizeXml(expected), normalizeXml(actual))
    }

    private void assertReportFileContents(String filename, String expected) {
        def actual = new File(filename).text
        assertEquals(normalizeXml(expected), normalizeXml(actual))
    }

    /**
     * Normalize the XML string. Remove all whitespace between elements, and normalize line-endings.
     * @param xml - the input XML string to normalize
     * @return the normalized XML
     */
    static String normalizeXml(String xml) {
        assert xml != null
        def resultXml = xml.replaceAll(/\>\s*\</, '><').trim()
        return resultXml.replace('\r\n', '\n')
    }

}
