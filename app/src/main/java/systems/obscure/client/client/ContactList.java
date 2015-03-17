package systems.obscure.client.client;

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

    public  void swap(int i, int j) {
        Contact temp = contactList[i];
        contactList[i] = contactList[j];
        contactList[j] = temp;
    }
    //TODO sort contacts list
}
