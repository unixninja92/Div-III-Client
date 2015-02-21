package systems.obscure.servertesting.client;

/**
 * @author unixninja92
 */
// contactList is a sortable slice of Contacts.
public class ContactList {
    Contact[] contactList;
    public int len() {
        return contactList.length;
    }
    public boolean less(int i, int j) {
        return contactList[i].name.compareTo(contactList[j].name) < 0;
    }
    //TODO sort contacts list
}
