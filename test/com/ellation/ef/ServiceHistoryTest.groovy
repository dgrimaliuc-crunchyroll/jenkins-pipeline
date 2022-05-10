package com.ellation.ef

import org.junit.Test
import static org.junit.Assert.*

class ServiceHistoryTest {

    @Test
    void emptyEntries() {
        def history = new ServiceHistory('prod-history-test ami-id')
        assertEquals('history-test', history.serviceName)
    }

    /**
     * We define an invalid entry one that doesn't have the right size (8)
     */
    @Test(expected = Exception)
    void errorOnInvalidEntries() {
        def invalidEntries = "history-test ami-id \n I am an invalid entry"
        new ServiceHistory(invalidEntries)
    }

    @Test
    void testOneValidLine() {
        def validEntry = "history-test ami-id \n" +
                'ami-f23ee18a 17 b15d6782bc9b73825a3611aee132f219f64dba5d 2017-11-26T07:34:18UTC arn:aws:iam::366843697376:user/toverton TwYTsRO4i989IqR83ZUYfbiGzzkaDUUl  stable'
        def history = new ServiceHistory(validEntry)

        assertEquals(1, history.entries.size())

        def entry = history.entries[0]

        assertEquals('ami-f23ee18a', entry['value'])
        assertEquals('17', entry['build_number'])
        assertEquals('b15d6782bc9b73825a3611aee132f219f64dba5d', entry['commit_hash'])
        assertEquals('2017-11-26T07:34:18UTC', entry['last_modified'])
        assertEquals('arn:aws:iam::366843697376:user/toverton', entry['modified_by'])
        assertEquals('TwYTsRO4i989IqR83ZUYfbiGzzkaDUUl', entry['version_id'])
        assertEquals('', entry['location'])
        assertEquals('stable', entry['status'])
    }

    @Test
    void testValidLineWithoutCommitAndBuild() {
        def validEntry = "history-test ami-id \n" +
                'ami-f23ee18a   2017-11-26T07:34:18UTC arn:aws:iam::366843697376:user/toverton TwYTsRO4i989IqR83ZUYfbiGzzkaDUUl  stable'
        def history = new ServiceHistory(validEntry)

        assertEquals(1, history.entries.size())

        def entry = history.entries[0]

        assertEquals('ami-f23ee18a', entry['value'])
        assertEquals('', entry['build_number'])
        assertEquals('', entry['commit_hash'])
        assertEquals('2017-11-26T07:34:18UTC', entry['last_modified'])
        assertEquals('arn:aws:iam::366843697376:user/toverton', entry['modified_by'])
        assertEquals('TwYTsRO4i989IqR83ZUYfbiGzzkaDUUl', entry['version_id'])
        assertEquals('', entry['location'])
        assertEquals('stable', entry['status'])
    }

    @Test
    void testValidLineWithoutBuild() {
        def validEntry = "history-test ami-id \n" +
                'ami-f23ee18a  sha 2017-11-26T07:34:18UTC arn:aws:iam::366843697376:user/toverton TwYTsRO4i989IqR83ZUYfbiGzzkaDUUl  stable'
        def history = new ServiceHistory(validEntry)

        assertEquals(1, history.entries.size())

        def entry = history.entries[0]

        assertEquals('ami-f23ee18a', entry['value'])
        assertEquals('', entry['build_number'])
        assertEquals('sha', entry['commit_hash'])
        assertEquals('2017-11-26T07:34:18UTC', entry['last_modified'])
        assertEquals('arn:aws:iam::366843697376:user/toverton', entry['modified_by'])
        assertEquals('TwYTsRO4i989IqR83ZUYfbiGzzkaDUUl', entry['version_id'])
        assertEquals('', entry['location'])
        assertEquals('stable', entry['status'])
    }

    @Test
    void multipleLineTest() {
        ServiceHistory history = buildRealHistoryObject()
        assertEquals(29, history.entries.size())

        def specificAmi = history.entries.find {
            it['value'] == 'ami-89d708f1' && it['status'] == 'undefined'
        }

        assertEquals('b15d6782bc9b73825a3611aee132f219f64dba5d', specificAmi['commit_hash'])
    }

    @Test
    void latest() {
        def history = buildRealHistoryObject()
        def latest = history.latest()
        assertEquals('ami-f23ee18a', latest['value'])
        assertEquals('17', latest['build_number'])
        assertEquals('2017-11-26T07:34:18UTC', latest['last_modified'])
    }

    @Test
    void testForEmptyHistory() {
        def history = buildEmptyHistory()
        def latest = history.latest()
        assertNull(latest)
    }

    @Test
    void latestStable() {
        def history = buildRealHistoryObject()
        def latest = history.latestStable()
        assertEquals('ami-f23ee18a', latest['value'])
        assertEquals('17', latest['build_number'])
        assertEquals('2017-11-26T07:34:18UTC', latest['last_modified'])
    }

    @Test
    void findAmi() {
        def history = buildRealHistoryObject()
        def entry   = history.find('ami-bdc916c5')

        assertEquals('16', entry['build_number'])
        assertEquals('rdSKFiLyYfrHY5gXUIUBOj9HTVkjaKUL', entry['version_id'])
    }

    @Test
    void serviceNameIsPassed() {
        def history = buildRealHistoryObject()

        def latest = history.latestStable()

        assertEquals('test-instance', latest.name)
    }

    private ServiceHistory buildRealHistoryObject() {
        def input = """staging-test-instance ami-id
ami-f23ee18a 17 b15d6782bc9b73825a3611aee132f219f64dba5d 2017-11-26T07:34:18UTC arn:aws:iam::366843697376:user/toverton TwYTsRO4i989IqR83ZUYfbiGzzkaDUUl  stable
ami-f23ee18a 17 b15d6782bc9b73825a3611aee132f219f64dba5d 2017-11-26T07:28:07UTC arn:aws:iam::366843697376:user/toverton XnEC0osIwInMrd6NV9ToA.PrnltGqnll  undefined
ami-bdc916c5 16 b15d6782bc9b73825a3611aee132f219f64dba5d 2017-11-25T15:35:41UTC arn:aws:iam::366843697376:user/toverton rdSKFiLyYfrHY5gXUIUBOj9HTVkjaKUL  stable
ami-bdc916c5 16 b15d6782bc9b73825a3611aee132f219f64dba5d 2017-11-25T15:29:30UTC arn:aws:iam::366843697376:user/toverton C3fcAmepAweIkCK3.bNUNaIRsx4CP8Lj  undefined
ami-89d708f1 15 b15d6782bc9b73825a3611aee132f219f64dba5d 2017-11-25T11:08:54UTC arn:aws:iam::366843697376:user/toverton ZWU3qDl9rr7t4cn1U8Omm4eGs5CHKZj0  undefined
ami-3bbf6043   2017-11-25T10:58:15UTC arn:aws:iam::366843697376:user/toverton Vio2NQ6pwtOQTm0NGsTNfyfenqzWWb0M  stable
ami-3bbf6043 14 b15d6782bc9b73825a3611aee132f219f64dba5d 2017-11-25T05:44:49UTC arn:aws:iam::366843697376:user/toverton aTKv3BG456uHhGDh59iya59dRUpq3h1S  undefined
ami-cabd62b2   2017-11-25T05:32:25UTC arn:aws:iam::366843697376:user/toverton Xn1svBuXA61eZVspetlksqg.LWAZic49  stable
ami-cabd62b2 10 b15d6782bc9b73825a3611aee132f219f64dba5d 2017-11-25T05:21:28UTC arn:aws:iam::366843697376:user/toverton dIeg99jt7gnz0hzFycDDEbF2jNIhtjsZ  undefined
ami-d2815eaa   2017-11-25T05:00:14UTC arn:aws:iam::366843697376:user/toverton Ip6xmwF1NQ4cfhuZVgNOqP.jxyiQlLqK  stable
ami-d2815eaa 9 b15d6782bc9b73825a3611aee132f219f64dba5d 2017-11-25T04:54:02UTC arn:aws:iam::366843697376:user/toverton WSvILQZ9Ifn_2gK9dVEk5us8Fz5BNwV8  undefined
ami-0588577d   2017-11-25T03:28:49UTC arn:aws:iam::366843697376:user/toverton o3SVxUoUeUsZnX97k33qKCG1xMZgvyJe  stable
ami-0588577d   2017-11-25T03:22:34UTC arn:aws:iam::366843697376:user/toverton BUistZIIrRu80dGSlxHPHw.bMeuTsIK1  undefined
ami-238d525b   2017-11-25T03:11:44UTC arn:aws:iam::366843697376:user/toverton VNK.qXVkNX3Kc5n6Vyd7JAZB1K38RVLY  stable
ami-238d525b   2017-11-25T03:05:31UTC arn:aws:iam::366843697376:user/toverton hvTAhxAD5RK1PPm65WteYbuj2mT301hZ  undefined
ami-208f5058   2017-11-25T02:35:14UTC arn:aws:iam::366843697376:user/toverton 0PXccQo2JD5mxvmOap7.6I5IrOXDV6HI  stable
ami-208f5058   2017-11-25T02:29:02UTC arn:aws:iam::366843697376:user/toverton chpfdR9DLUdAgfS6hyrDEsRdMTZcTFoS  undefined
ami-3a558942   2017-11-24T10:42:02UTC arn:aws:iam::366843697376:user/toverton RO09cVw.OfeBguKlKoN8qSKjxnJzTP.c  stable
ami-3a558942   2017-11-24T10:35:51UTC arn:aws:iam::366843697376:user/toverton 2_7gqG583JJksX5QXTxSv7P3x0sgoEgh  undefined
ami-385b8740   2017-11-24T10:17:17UTC arn:aws:iam::366843697376:user/toverton 44ic9BTgNW4wutE6ucSDNIjocPVzM3H1  stable
ami-385b8740   2017-11-24T10:11:03UTC arn:aws:iam::366843697376:user/toverton 2crX04K9AePZAUI.9fdD6op8L_AfoJKn  undefined
ami-c15488b9   2017-11-24T09:50:47UTC arn:aws:iam::366843697376:user/toverton M2wL.5zSIqfRi1fsYJ_OK_MXqeETs6sX  stable
ami-c15488b9   2017-11-24T09:44:30UTC arn:aws:iam::366843697376:user/toverton uWcnn.bCrcxhuBTiz5TmJS3vGgyq_WfH  undefined
ami-bbf529c3   2017-11-23T16:53:13UTC arn:aws:iam::366843697376:user/toverton H3xwbW8rT8aroaUux3GqGf_PiuJpW1.S  stable
ami-bbf529c3   2017-11-23T16:46:58UTC arn:aws:iam::366843697376:user/toverton 4I8t.0lBfXd3HJ7gf4ZHFSWeWeOTUypv  undefined
ami-36068f56   2017-05-26T18:13:42UTC arn:aws:iam::366843697376:user/toverton bzQTFUJ1_pNhCqYIedLpTh6SN3en5nzL  undefined
ami-11111111   2017-05-26T18:10:41UTC arn:aws:iam::366843697376:user/toverton Yv0vUv2k7.0gi1LBJCp6tbWEJb6Zi2TA  undefined
ami-36068f56   2017-05-26T06:16:17UTC arn:aws:iam::366843697376:user/toverton k441Y5W0rfU2hihAMZPn5tNSvrGVn6IM  undefined
=latest   2017-03-01T01:54:49UTC arn:aws:iam::366843697376:user/toverton MoVVD.mACCWa5UE8WytpjGi7PyYTdR2s  undefined
"""
        def history = new ServiceHistory(input)
        history
    }

    private ServiceHistory buildEmptyHistory() {
        String input = "staging-test-instance ami-id"
        ServiceHistory history = new ServiceHistory(input)
        return history
    }

}
