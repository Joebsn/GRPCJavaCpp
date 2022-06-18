package servicespackagesexercice.exercice.project;

import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.netty.handler.ssl.SslContext;
import servicespackagesexercice.exercice.project.UserServiceGrpc.UserServiceBlockingStub;
import servicespackagesexercice.exercice.project.UserServiceGrpc.UserServiceStub;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

public class BooksClient
{
    private static final Logger logger = Logger.getLogger(BooksClient.class.getName());
    private final ManagedChannel channel;
    private final UserServiceBlockingStub blockingStub;
    private final UserServiceStub asyncStub;

    public BooksClient(String host, int port)
    {
        channel = ManagedChannelBuilder.forAddress("localhost",port).usePlaintext().build();
        blockingStub = UserServiceGrpc.newBlockingStub(channel);
        asyncStub = UserServiceGrpc.newStub(channel);
    }

    public void shutdown() throws InterruptedException
    {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS); //wait for channel to terminate for at most 5 seconds
    }

    public void testLogin(String name, String password)
    {
        LoginRequest loginrequest = LoginRequest.newBuilder().setUsername(name).setPassword(password).build();
        APIResponse response = APIResponse.getDefaultInstance();

        try
        {
            //setting deadline of the request to be 5 sec
            response = blockingStub.withDeadlineAfter(5, TimeUnit.SECONDS).login(loginrequest);
        }
        catch (Exception e)
        {
            logger.log(Level.SEVERE, "request failed: " + e.getMessage());
            return;
        }
        logger.info(response.getResponsemessage());
    }

    public void testSearchByAuthor(String AuthorName)
    {
        //ServerStreaming rpc
        BookSearch requestmany = BookSearch.newBuilder().setAuthor(AuthorName).build();
        Iterator<Book> bookresponsemany;
        try
        {
            bookresponsemany = blockingStub.withDeadlineAfter(5, TimeUnit.SECONDS).searchByAuthor(requestmany);
            while(bookresponsemany.hasNext())
            {
                Book b = bookresponsemany.next();
                logger.info("Found book: " + b.getName() + " and it costs: " + b.getPrice());
            }
        }
        catch (StatusRuntimeException e)
        {
            logger.info("RPC failed: " + e.getStatus());
            return;
        }
        logger.info("search completed");
    }

    public void testTotalCartValue() throws InterruptedException {
        final CountDownLatch finishLatch = new CountDownLatch(1); //to wait until the whole process is completed because asynchroneous

        //public StreamObserver<Book> totalCartValue(StreamObserver<Cart> responseObserver) take a StreamObserver<Cart> as argument so we defined it here
        StreamObserver<Book> streamClientSender = asyncStub.withDeadlineAfter(5, TimeUnit.SECONDS).totalCartValue
                (
                new StreamObserver<Cart>()
                {
                    @Override
                    public void onNext(Cart value)
                    {
                        logger.info("Order summary:" + "\nTotal number of Books:" + value.getBooks() + "\nTotal Order Value:" + value.getPrice());
                    }

                    @Override
                    public void onError(Throwable t)
                    {
                        logger.log(Level.SEVERE, "upload failed: " + t);
                        finishLatch.countDown();
                    }

                    @Override
                    public void onCompleted()
                    {
                        logger.info("Operation completed");
                        finishLatch.countDown();
                    }
                }
        );

        try
        {
            String bookName1 = "Book2", bookName2 = "Book3";
            //calling the stream
            Book request = Book.newBuilder().setName(bookName1).build();
            streamClientSender.onNext(request);

            Book request2 = Book.newBuilder().setName(bookName2).build();
            streamClientSender.onNext(request2);

            logger.info("Done, waiting for server to create order summary...");
        }
        catch (Exception e)
        {
            logger.log(Level.SEVERE, "unexpected error: " + e.getMessage());
            streamClientSender.onError(e); //report the error to the server
            return;
        }
        streamClientSender.onCompleted();

        if (!finishLatch.await(1, TimeUnit.MINUTES)) //to wait for the response thread to finish (here wait for 1 minutes)
        {
            logger.warning("request cannot finish within 1 minute");
        }
    }

    public void testliveCartValue() throws InterruptedException 
    {
        final CountDownLatch finishLatch = new CountDownLatch(1); //wait for the response stream to finish

        StreamObserver<Book> streamClientSender = asyncStub.withDeadlineAfter(5, TimeUnit.SECONDS).liveCartValue
        (
            new StreamObserver<Cart>()
            {
                @Override
                public void onNext(Cart value) //when the onNext function from BooksService function is called: responseObserver.onNext(Cart.newBuilder().setPrice(cartValue).setBooks(size).build());
                // this function will be called (so when we get a book)
                {
                    logger.info("Order summary:" + "\nTotal number of Books:" + value.getBooks() + "\nTotal Order Value:" + value.getPrice());
                }

                @Override
                public void onError(Throwable t)
                {
                    logger.log(Level.SEVERE, "upload failed: " + t);
                    finishLatch.countDown();
                }

                @Override
                public void onCompleted()
                {
                    logger.info("Operation completed");
                    finishLatch.countDown();
                }
            }
        );

        try
        {
            String bookName1 = "Book2", bookName2 = "Book3";
            logger.info("Adding book with title starting with: " + bookName1);

            //calling the stream
            Book request = Book.newBuilder().setName(bookName1).build();
            streamClientSender.onNext(request); //will call public void onNext(Book book) in BooksService.java

            logger.info("Adding book with title starting with: " + bookName2);
            Book request2 = Book.newBuilder().setName(bookName2).build();
            streamClientSender.onNext(request2); //will call public void onNext(Book book) in BooksService.java
        }
        catch (Exception e)
        {
            logger.log(Level.SEVERE, "unexpected error: " + e.getMessage());
            streamClientSender.onError(e);
            return;
        }

        logger.info("Done, waiting for server to create order summary...");
        streamClientSender.onCompleted();

        if (!finishLatch.await(1, TimeUnit.MINUTES)) //wait for the response stream
        {
            logger.warning("request cannot finish within 1 minute");
        }
    }

    public void testmetadataAndInterceptors(String bookName) throws InterruptedException
    {
        String serverAddress = "localhost:8080";
        ManagedChannel channel1 =  ManagedChannelBuilder.forTarget(serverAddress).usePlaintext().intercept(new BookClientInterceptor()).build();

        UserServiceBlockingStub blockingStub1 = UserServiceGrpc.newBlockingStub(channel1);
        UserServiceStub asyncStub1 = UserServiceGrpc.newStub(channel1);
        try
        {
            BooksClient client = new BooksClient("0.0.0.0", 8080);
            client.getBook(bookName);
        }
        finally
        {
            channel1.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    public static void main(String[] args) throws InterruptedException //throws NoSuchAlgorithmException, CertificateException, IOException, KeyStoreException
    {
        BooksClient client = new BooksClient("0.0.0.0", 8080);

        try
        {
            //client.testLogin("Joe", "Joe");
            //client.testLogin("Joe", "j");
            //client.testSearchByAuthor("Author3");
            //client.testTotalCartValue();
            client.testliveCartValue();

            //String bookName = "Book_4";
            //client.testmetadataAndInterceptors(bookName);
        }
        finally
        {
            client.shutdown();
        }

    }


    //The interceptor
    static class BookClientInterceptor implements ClientInterceptor
    {
        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT>
        interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next)
        {
            return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions))
            {
                @Override
                public void start(Listener<RespT>responseListener, Metadata headers)
                {
                    logger.info("Added metadata");
                    headers.put(Metadata.Key.of("HOSTNAME", ASCII_STRING_MARSHALLER), "MY_HOST");
                    super.start(responseListener, headers);
                }
            };
        }
    }

    public void getBook(String bookName)
    {
        logger.info("Querying for book with title: " + bookName);
        BookSearch request = BookSearch.newBuilder().setName(bookName).build();
        Book response;
        CallOptions.Key<String> metaDataKey = CallOptions.Key.create("my_key");
        try
        {
            response = blockingStub.withOption(metaDataKey, "bar").first(request);
        }
        catch (StatusRuntimeException e)
        {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Got following book from server: " + response);
    }
}


