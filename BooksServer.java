package servicespackagesexercice.exercice.project;

import java.lang.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;

public class BooksServer {
    private static final Logger logger = Logger.getLogger(BooksServer.class.getName());
    private final int port;
    private final Server server;

    public BooksServer(int port) {
        this(ServerBuilder.forPort(port), port);
    }

    public BooksServer(ServerBuilder serverBuilder, int port) {
        this.port = port;
        BooksService booksservice = new BooksService();
        server = serverBuilder.addService(booksservice).build();
    }

    public void start() throws IOException {
        server.start();
        logger.info("Server started on port: " + port);

        //grpc server shutdown well cz maybe it will be terminated externally
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.err.println("shut down gRPC server because JVM shuts down");
                try {
                    BooksServer.this.stop();
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
                System.err.println("server shut down");
            }
        });
    }

    public void stop() throws InterruptedException {
        if (server != null) {
            //wait until server is completely terminated
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException
    {
        /*
        BooksServer server = new BooksServer(8080);
        server.start();
        server.blockUntilShutdown();
        */


        //For interceptors
        final BooksServer greetServer = new BooksServer(8080);
        greetServer.startfunction();
    }


    class BookServerInterceptor implements ServerInterceptor
    {
        @Override
        public <ReqT, RespT> Listener<ReqT>
        interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next)
        {
            logger.info("Received following metadata: " + headers);
            return next.startCall(call, headers);
        }
    }

    private void startfunction() throws IOException, InterruptedException
    {
        int port = 8080;
        Server server = ServerBuilder.forPort(port).addService(new BooksService()).intercept(new BookServerInterceptor()).build().start();
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
                System.err.println("Shutting down gRPC server");
                try
                {
                    server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace(System.err);
                }
            }
        });
        server.awaitTermination();
    }
}
