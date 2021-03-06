package com.ellation.ios

import com.cloudbees.groovy.cps.NonCPS

class PlistParser {
    @NonCPS
    static parseXmlPlistText(String text) {
        def parser = new XmlParser()
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
        def xml = parser.parseText(text)

        assert xml.name() == "plist"

        def parseNode
        parseNode = { node ->
            def childNodes = node."*"
            switch (node.name()) {
                case "dict":
                    def dict = [:]
                    for (int i = 0; i < childNodes.size(); i += 2) {
                        assert childNodes[i].name() == "key"
                        dict[childNodes[i].text().trim()] = parseNode(childNodes[i + 1])
                    }
                    return dict
                case "array":
                    return childNodes.collect(parseNode)
                case "string":
                    return node.text()
                case "integer":
                    return Integer.parseInt(node.text())
                case "date":
                    return Date.parse("yyyy-MM-dd'T'HH:mm:ss'Z'", node.text())
                case "false":
                    return false
                case "true":
                    return true
                case "data":
                    return node.text()
            }
        }

        return parseNode(xml."*"[0])
    }
}
