/**
 * Helper function to convert a comma delimited string to List
 * @param commaList
 * @return
 */
List<String> convertCommaStringToArray(String commaList) {
    if (!commaList?.trim()) {
        return []
    }

    List<String> list = commaList.split(",")
    for(int i = 0; i < list.size(); i++) {
        list[i]= list[i].trim()
    }
    return list
}
