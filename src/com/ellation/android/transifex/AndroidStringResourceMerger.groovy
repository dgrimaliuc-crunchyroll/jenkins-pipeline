package com.ellation.android.transifex

import com.cloudbees.groovy.cps.NonCPS
import groovy.xml.XmlUtil
import org.xml.sax.InputSource

class AndroidStringResourceMerger {
    /**
     * Merges Android strings.xml translation files of different modules into one.
     *
     * @param filesContent the content of strings.xml files to be merged.
     */
    @NonCPS
    static String mergeResources(List<String> filesContent) {
        XmlParser xmlParser = new XmlParser()
        def mainNode = xmlParser.parse(new InputSource(new StringReader(filesContent[0])))
        filesContent.drop(1).each {
            InputSource inputSource = new InputSource(new StringReader(it))
            def node = xmlParser.parse(inputSource)
            node.children().each { mainNode.append(it) }
        }
        return XmlUtil.serialize(mainNode)
    }
}
