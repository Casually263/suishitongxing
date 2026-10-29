package com.suishi.ai.provider;

import com.suishi.ai.config.AiProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;

@Component
public class AiEndpointPolicy {
    private final AiProperties properties;
    private final boolean development;
    private final ExecutorService dns=new ThreadPoolExecutor(2,2,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4),r->{Thread t=new Thread(r,"ai-dns");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    public AiEndpointPolicy(AiProperties properties,Environment environment){this.properties=properties;this.development=environment.acceptsProfiles(Profiles.of("dev","test"));}
    public URI endpoint(String baseUrl){
        try {
            URI base=URI.create(baseUrl.strip());String host=base.getHost();String path=Objects.requireNonNullElse(base.getRawPath(),"");
            if(host==null || base.getPort()>65535 || base.getPort()==0 || base.getUserInfo()!=null || base.getRawQuery()!=null || base.getRawFragment()!=null || path.contains("..") || !path.matches("/[A-Za-z0-9_./-]*|"))throw new IllegalArgumentException("Invalid AI endpoint");
            String origin=origin(base);
            boolean allowed=properties.getAllowedOrigins().stream().anyMatch(value->{try{return origin(URI.create(value.strip())).equals(origin);}catch(Exception ignored){return false;}});
            if(!allowed)throw new IllegalArgumentException("AI origin is not in the server allowlist");
            boolean literalLoopback=Set.of("127.0.0.1","::1","[::1]").contains(host.toLowerCase(Locale.ROOT));
            boolean localAllowed=development && properties.isAllowLocalEndpoints() && literalLoopback;
            if(!"https".equalsIgnoreCase(base.getScheme()) && !(localAllowed && "http".equalsIgnoreCase(base.getScheme())))throw new IllegalArgumentException("HTTPS required");
            Future<InetAddress[]> lookup=dns.submit(()->InetAddress.getAllByName(host));
            InetAddress[] addresses;
            try{addresses=lookup.get(1,TimeUnit.SECONDS);}catch(Exception failure){lookup.cancel(true);if(failure instanceof InterruptedException)Thread.currentThread().interrupt();throw new IllegalArgumentException("AI host lookup unavailable");}
            for(InetAddress address:addresses){
                byte[] bytes=address.getAddress();boolean uniqueLocal=bytes.length==16 && (bytes[0]&0xfe)==0xfc;
                if((address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isMulticastAddress() || uniqueLocal) && !localAllowed)throw new IllegalArgumentException("Private AI address prohibited");
            }
            return URI.create(base.toString().replaceAll("/+$","")+"/chat/completions");
        } catch(RejectedExecutionException e){throw new IllegalArgumentException("AI host lookup busy");}
    }
    @PreDestroy public void close(){dns.shutdownNow();}
    private static String origin(URI uri){
        if(uri.getScheme()==null || uri.getHost()==null)throw new IllegalArgumentException();
        int port=uri.getPort();String scheme=uri.getScheme().toLowerCase(Locale.ROOT);
        if(port==-1 || port==443 && scheme.equals("https") || port==80 && scheme.equals("http"))return scheme+"://"+uri.getHost().toLowerCase(Locale.ROOT);
        return scheme+"://"+uri.getHost().toLowerCase(Locale.ROOT)+":"+port;
    }
}
