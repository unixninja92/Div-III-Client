package systems.obscure.client.disk;

import java.io.IOException;

import systems.obscure.client.protos.LocalStorage;

/**
 * @author unixninja92
 * ErasureStorage represents a type of storage that can store, and erase, small
 * amounts of data.
 */
public interface ErasureStorage {
    // Create creates a new erasure storage object and fills out header to
    // include the needed values.
    public void Create(LocalStorage.Header header, byte[] key);

    // Read reads the current value of the storage.
    public byte[] Read(byte[] key);

    // Write requests that the given value be stored and the old value
    // forgotten.
    public void Write(byte[] key, byte[] value) throws IOException;

    // Destroy erases the NVRAM entry.
    public void Destroy(byte[] key);
}
