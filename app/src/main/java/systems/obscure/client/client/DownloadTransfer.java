package systems.obscure.client.client;

import java.io.FileReader;
import java.io.IOException;

import systems.obscure.client.protos.Pond;

/**
 * @author unixninja92
 */
public class DownloadTransfer implements DetachmentTransfer{
    long fileID;
    FileReader file;
    long resume;
    byte[] from;

    @Override
    public Pond.Request Request() {
        try {
            file.skip(0);//Fixme get from end?
            //TODO finish this
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public ProcessedDetachmentReply ProcessReply(Pond.Reply reply) throws IOException {
        ProcessedDetachmentReply processed = new ProcessedDetachmentReply();

        if(!reply.hasDownload())
            throw new IOException("Reply from server didn't include a download section");

        processed.total = reply.getDownload().getSize();
        if(processed.total < resume)
            throw new IOException("Reply from server suggested that the file was truncated");

        processed.offset = resume;
        processed.file = file;
        return processed;
    }

    @Override
    public boolean Complete(Transport trans) {
        return true;
    }
}
