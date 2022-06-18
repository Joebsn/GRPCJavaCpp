package servicespackagesexercice.exercice.project;
import io.grpc.Context;
import io.grpc.Status;
import servicespackagesexercice.exercice.project.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.grpc.stub.StreamObserver;

public class BooksService extends UserServiceGrpc.UserServiceImplBase
{
    private static final Logger logger = Logger.getLogger(BooksService.class.getName());

    Map<String, Book> bookMap = new HashMap<String, Book>()
    {{
        put("Book_1", Book.newBuilder().setName("Book1").setAuthor("Author1").setPrice(300).build());
        put("Book_2", Book.newBuilder().setName("Book2").setAuthor("Author2").setPrice(400).build());
        put("Book_3", Book.newBuilder().setName("Book3").setAuthor("Author3").setPrice(500).build());
        put("Book_4", Book.newBuilder().setName("Book4").setAuthor("Author4").setPrice(600).build());
        put("Book_5", Book.newBuilder().setName("Book5").setAuthor("Author3").setPrice(700).build());
    }};

    public BooksService(){}

    @Override
    public void login(LoginRequest request, StreamObserver<APIResponse> responseObserver)
    {
        logger.info("Inside login");
        String username = request.getUsername();
        String password = request.getPassword();
        APIResponse.Builder response = APIResponse.newBuilder();

        if(username.equals(password))
        {
            response.setResponseCode(0).setResponsemessage("SUCCESS");
            logger.info("Logged IN");
        }
        else
        {
            response.setResponseCode(1).setResponsemessage("INVALID INFORMATIONS");
            logger.info("INVALID INFORMATIONS");
        }

        // heavy processing
        /*
        try {
            TimeUnit.SECONDS.sleep(6);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        */

        if(Context.current().isCancelled())
        {
            logger.info("Request is cancelled");
            responseObserver.onError(Status.CANCELLED.withDescription("request is cancelled").asRuntimeException());
            return;
        }

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void searchByAuthor(BookSearch booksearch, StreamObserver<Book> responseObserver)
    {
        String authorName = booksearch.getAuthor();
        logger.info("Searching for book written by: " + authorName);

        for (java.util.Map.Entry<String, Book> bookEntry : bookMap.entrySet())
        {
            try
            {
                System.out.println("Going through more books....");
                Thread.sleep(200);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            if(bookEntry.getValue().getAuthor().startsWith(booksearch.getAuthor()))
            {
                logger.info("Found book with required author: " + bookEntry.getValue().getName()+ ". Sending....");
                responseObserver.onNext(bookEntry.getValue());
            }
        }
        responseObserver.onCompleted();
    }


    @Override
    public StreamObserver<Book> totalCartValue(StreamObserver<Cart> responseObserver) //take a StreamObserver<Cart> as argument
    {
        return new StreamObserver<Book>()
        {
            int cartValue = 0, size = 0;

            @Override
            public void onNext(Book book)
            {
                logger.info("Searching for a book with title starting with: " + book.getName());
                for(Entry<String, Book> bookEntry : bookMap.entrySet())
                {
                    if(bookEntry.getValue().getName().startsWith(book.getName()))
                    {
                        logger.info("Found book, adding to cart:....");
                        cartValue += bookEntry.getValue().getPrice();
                        size += 1;
                    }
                }
            }

            @Override
            public void onError(Throwable t)
            {
                logger.info("Error while reading book stream: " + t);
            }

            @Override
            public void onCompleted()
            {
                Cart response = Cart.newBuilder().setPrice(cartValue).setBooks(size).build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public StreamObserver<Book>liveCartValue(StreamObserver<Cart> responseObserver)
    {
        return new StreamObserver<Book>()
        {
            int cartValue = 0, size = 0;

            @Override
            public void onNext(Book book)
            {
                logger.info("Searching for a book with title starting with: " + book.getName());
                for(Entry<String, Book> bookEntry : bookMap.entrySet())
                {
                    if(bookEntry.getValue().getName().startsWith(book.getName()))
                    {
                        logger.info("Found book, adding to cart:....");
                        cartValue += bookEntry.getValue().getPrice();
                        size += 1;
                    }
                }
                logger.info("Updating cart value...");
                responseObserver.onNext(Cart.newBuilder().setPrice(cartValue).setBooks(size).build()); // will call public void onNext(Cart value) in BooksClient.java
            }

            @Override
            public void onError(Throwable t)
            {
                logger.info("Error while reading book stream: " + t);
            }
            @Override
            public void onCompleted()
            {
                logger.info("Order completed");
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void first(BookSearch searchQuery, StreamObserver<Book> responseObserver)
    {
        logger.info("Searching for book with title: " + searchQuery.getName());
        List<String> matchingBookTitles = bookMap.keySet().stream().filter(title ->title.startsWith(searchQuery.getName().trim())).collect(Collectors.toList());
        Book foundBook = null;
        if(matchingBookTitles.size() > 0)
        {
            foundBook = bookMap.get(matchingBookTitles.get(0));
        }
        responseObserver.onNext(foundBook);
        responseObserver.onCompleted();
    }

}

