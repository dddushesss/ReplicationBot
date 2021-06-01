package com.example.replicationbot;


import lombok.SneakyThrows;
import lombok.var;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.*;
import java.net.URL;

@Component
public class TelegramBot extends TelegramLongPollingBot {
    String userName;
    String token;

    public TelegramBot() {
        userName = "ReplicationOriFinder_bot";
        token = "1713139113:AAG3RRKkSKPE3FAe8bY4CBolid4iFvSBJhk";
    }

    @Override
    public String getBotUsername() {
        return userName;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    private void getFile(String file_name, String file_id) throws IOException {
        URL url = new URL("https://api.telegram.org/bot" + token + "/getFile?file_id=" + file_id);

        BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
        String getFileResponse = br.readLine();

        JSONObject jresult = new JSONObject(getFileResponse);
        JSONObject path = jresult.getJSONObject("result");
        String filepath = path.getString("file_path");

        File localFile = new File("src/main/resources/uploaded_files/" + file_name);
        InputStream is = new URL("https://api.telegram.org/file/bot" + token + "/" + filepath).openStream();
        FileUtils.copyInputStreamToFile(is, localFile);

    }

    @SneakyThrows
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            execute(new SendMessage(String.valueOf(update.getMessage().getChatId()), update.getMessage().getText()));
        } else if (update.hasMessage() && update.getMessage().hasDocument()) {
            var f = update.getMessage().getDocument();
            getFile(f.getFileName(), f.getFileId());
            SendPhoto sendPhoto = new SendPhoto();
            ReplicationPointFinder replicationPointFinder = new ReplicationPointFinder("src/main/resources/uploaded_files/" + f.getFileName());
            var img = replicationPointFinder.getGCImage();
            InputFile inputFile = new InputFile();
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write((RenderedImage) img, "png", os);
            InputStream fis = new ByteArrayInputStream(os.toByteArray());
            inputFile.setMedia(fis, "GC.png");
            sendPhoto.setPhoto(inputFile);
            sendPhoto.setChatId(String.valueOf(update.getMessage().getChatId()));
            execute(sendPhoto);
            if (replicationPointFinder.getResultMessage().length() > 0) {
                execute(new SendMessage(String.valueOf(update.getMessage().getChatId()), replicationPointFinder.getResultMessage()));
            } else {
                img = replicationPointFinder.getKmerCounts();
                inputFile = new InputFile();
                os = new ByteArrayOutputStream();
                ImageIO.write((RenderedImage) img, "png", os);
                fis = new ByteArrayInputStream(os.toByteArray());
                inputFile.setMedia(fis, "kmer.png");
                sendPhoto.setPhoto(inputFile);
                sendPhoto.setChatId(String.valueOf(update.getMessage().getChatId()));
                execute(sendPhoto);

                String ori = replicationPointFinder.getOri();
                String kmer = replicationPointFinder.getKmer();

                execute(new SendMessage(String.valueOf(update.getMessage().getChatId()), kmer + " - " + replicationPointFinder.getMaxIndex() + "\n\n" + ori));
            }
            File localFile = new File("src/main/resources/uploaded_files/" + f.getFileName());

            FileUtils.deleteQuietly(localFile);
        }
    }
}
