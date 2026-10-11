package com.twcodeart.tcgnews.subscription;

import com.twcodeart.tcgnews.news.Game;
import java.util.Locale;

public record NewsGroupCommand(Action action,Game game){
    public enum Action { SUBSCRIBE,UNSUBSCRIBE,STATUS,HELP,INVALID }
    public boolean changesSubscription(){return action==Action.SUBSCRIBE||action==Action.UNSUBSCRIBE;}
    public static NewsGroupCommand parse(String text){
        if(text==null||text.length()>200)return null;
        String[] parts=text.strip().toLowerCase(Locale.ROOT).split("\\s+");
        if(!parts[0].equals("/news"))return null;
        if(parts.length==1)return new NewsGroupCommand(Action.HELP,null);
        if(parts.length==2&&parts[1].equals("help"))return new NewsGroupCommand(Action.HELP,null);
        if(parts.length==2&&parts[1].equals("status"))return new NewsGroupCommand(Action.STATUS,null);
        if(parts.length==3){
            Game game=switch(parts[2]){case "ws"->Game.WEISS_SCHWARZ;case "ocg"->Game.YUGIOH_OCG;default->null;};
            if(game!=null&&parts[1].equals("subscribe"))return new NewsGroupCommand(Action.SUBSCRIBE,game);
            if(game!=null&&parts[1].equals("unsubscribe"))return new NewsGroupCommand(Action.UNSUBSCRIBE,game);
        }
        return new NewsGroupCommand(Action.INVALID,null);
    }
}
