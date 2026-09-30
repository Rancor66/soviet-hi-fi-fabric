package ru.daniel.soviethifi.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import ru.daniel.soviethifi.network.Network;
import java.nio.file.*;
import java.util.*;

/** A compact aluminium-front panel; no container transaction is handled client-side. */
public final class HiFiScreen extends Screen {
    private final Network.OpenUi target;
    private Network.Panel panel;
    private EditBox path,titleBox;
    private int left,top,refresh;
    private String selected="",label="";
    private final List<Button> rackButtons=new ArrayList<>();
    public HiFiScreen(Network.OpenUi target){super(Component.literal(target.kind()==0?"ЗАПИСЬ КАССЕТЫ":target.kind()==2?"ФОНОТЕКА":"МАЯК / АМФИТОН"));this.target=target;}
    @Override protected void init(){
        left=(width-320)/2;top=(height-228)/2;rackButtons.clear();
        if(target.kind()==0){
            path=new EditBox(font,left+16,top+57,228,20,Component.literal("Путь к аудиофайлу"));path.setMaxLength(2048);path.setValue(selected);path.setHint(Component.literal("Выберите или перетащите файл"));addRenderableWidget(path);
            button("Файл…",248,57,56,()->browse());
            titleBox=new EditBox(font,left+16,top+98,288,20,Component.literal("Название кассеты"));titleBox.setMaxLength(80);titleBox.setValue(label);titleBox.setHint(Component.literal("Исполнитель — название"));addRenderableWidget(titleBox);
            button("ЗАПИСАТЬ КАССЕТУ",16,133,288,()->{selected=path.getValue();label=titleBox.getValue();try{HiFiClient.record(Path.of(selected.replaceAll("^\"|\"$","")),label,target.offHand());}catch(Exception e){HiFiClient.message("Выберите аудиофайл");}});
        }else if(target.kind()==1){
            button("▶ / Ⅱ",16,83,88,()->control(1,0));button("■ СТОП",116,83,88,()->control(2,0));button("ИЗВЛЕЧЬ",216,83,88,()->control(3,0));
            button("−",168,117,26,()->control(4,(panel==null?.65f:panel.volume())-.1f));button("+",278,117,26,()->control(4,(panel==null?.65f:panel.volume())+.1f));
            button("−",168,145,26,()->control(5,(panel==null?.35f:panel.tape())-.1f));button("+",278,145,26,()->control(5,(panel==null?.35f:panel.tape())+.1f));
            button("Мой звук: −",16,178,100,()->HiFiClient.personalVolume=Math.max(0,HiFiClient.personalVolume-.1f));
            button("Мой звук: +",124,178,100,()->HiFiClient.personalVolume=Math.min(1,HiFiClient.personalVolume+.1f));
            control(0,0);
        }else{
            for(int i=0;i<12;i++){final int index=i;Button b=button("—",16+(i%2)*148,46+(i/2)*24,140,()->control(6,index));rackButtons.add(b);}
            control(0,0);
        }
        button("ГОТОВО",234,200,70,this::onClose);
    }
    private Button button(String text,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(Component.literal(text),_->action.run()).bounds(left+x,top+y,w,20).build());}
    private void control(int action,float value){Network.sendServer(new Network.Control(target.pos(),action,value));}
    public void panel(Network.Panel value){
        if(!value.pos().equals(target.pos()))return;panel=value;
        for(int i=0;i<rackButtons.size();i++){Button b=rackButtons.get(i);b.active=i<value.titles().size();b.setMessage(Component.literal(b.active?(i+1)+". "+font.plainSubstrByWidth(value.titles().get(i),110):"—"));}
    }
    @Override public void tick(){if(target.kind()!=0&&++refresh%20==0)control(0,0);}
    private void browse(){
        try(MemoryStack stack=MemoryStack.stackPush()){
            var filters=stack.mallocPointer(2);filters.put(stack.UTF8("*.mp3")).put(stack.UTF8("*.ogg")).flip();
            String file=TinyFileDialogs.tinyfd_openFileDialog("Музыка для кассеты",path.getValue(),filters,"MP3 / OGG Vorbis",false);
            if(file!=null)select(Path.of(file));
        }catch(Exception e){HiFiClient.message("Перетащите файл в окно или укажите его путь");}
    }
    private void select(Path file){selected=file.toAbsolutePath().toString();path.setValue(selected);if(titleBox.getValue().isBlank())titleBox.setValue(file.getFileName().toString().replaceFirst("\\.[^.]+$",""));}
    @Override public void onFilesDrop(List<Path> files){if(target.kind()==0&&!files.isEmpty())select(files.getFirst());}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partial){
        g.fill(left-4,top-4,left+324,top+232,0xff332219);g.fill(left,top,left+320,top+228,0xffc6c1ad);
        g.fill(left+8,top+8,left+312,top+32,0xff232922);g.text(font,title,left+16,top+16,0xffd8ba72);
        for(int y=top+34;y<top+225;y+=3)g.horizontalLine(left+8,left+311,y,0x10746e5e);
        if(target.kind()==0){
            g.text(font,"АУДИОФАЙЛ",left+16,top+43,0xff35352d);g.text(font,"НАДПИСЬ НА КАССЕТЕ",left+16,top+84,0xff35352d);
            g.textWithWordWrap(font,Component.literal(HiFiClient.status),left+16,top+165,288,0xff35352d);
            g.text(font,"Держите кассету в руке до конца записи",left+16,top+211,0xff474637);
        }else if(target.kind()==1){
            g.fill(left+16,top+43,left+304,top+75,0xff17291f);
            String name=panel==null?"Подключение…":panel.titles().isEmpty()?"Вставьте кассету":panel.titles().getFirst();
            g.text(font,font.plainSubstrByWidth(name,274),left+22,top+49,0xff9cce86);
            g.text(font,panel!=null&&panel.playing()?"● ВОСПРОИЗВЕДЕНИЕ":"Ⅱ ПАУЗА / ОЖИДАНИЕ",left+22,top+62,0xffd4bd72);
            g.text(font,"ГРОМКОСТЬ",left+16,top+123,0xff35352d);g.text(font,"ТЁПЛАЯ ЛЕНТА",left+16,top+151,0xff35352d);
            g.centeredText(font,Math.round((panel==null?.65f:panel.volume())*100)+"%",left+236,top+123,0xff35352d);
            g.centeredText(font,Math.round((panel==null?.35f:panel.tape())*100)+"%",left+236,top+151,0xff35352d);
            g.text(font,"Личный уровень: "+Math.round(HiFiClient.personalVolume*100)+"%",left+16,top+207,0xff35352d);
        }else g.text(font,"Нажмите на кассету, чтобы забрать её",left+16,top+204,0xff35352d);
        super.extractRenderState(g,mouseX,mouseY,partial);
    }
}
