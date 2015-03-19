package systems.obscure.client.client;

import java.io.FileReader;
import java.io.IOException;

import systems.obscure.client.protos.Pond;

/**
 * @author unixninja92
 */
public class UploadTransfer implements DetachmentTransfer {
    long id;
    FileReader file;
    long total;

    @Override
    public Pond.Request Request() {
        Pond.Request.Builder request = Pond.Request.newBuilder();
        Pond.Upload.Builder upload = Pond.Upload.newBuilder();
        upload.setId(id);
        upload.setSize(total);
        request.setUpload(upload.build());
        return request.build();
    }

    @Override
    public ProcessedDetachmentReply ProcessReply(Pond.Reply reply) throws IOException {
        ProcessedDetachmentReply processed = new ProcessedDetachmentReply();
        if(reply.getUpload() != null && reply.getUpload().hasResume())
            processed.offset = reply.getUpload().getResume();

        if(processed.offset == total){
            processed.isComplete = true;
            return processed;
        }

        if(processed.offset > total)
            throw new IOException("offset from server is greater than the length of the file: " + processed.offset+" vs "+ total);
        long  pos = file.skip(processed.offset);
        if(pos != processed.offset)
            throw new IOException("failed to seek in temp file: "+pos+" "+processed.offset);

        processed.file = file;
        processed.isUpload = true;
        processed.total = total;
        return processed;
    }

    @Override
    public boolean Complete(Transport trans) {
        // The server will send us a zero byte if it got everything.
        try {
            return trans.readByte() == 0;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
