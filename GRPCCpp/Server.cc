#include <chrono>
#include <iostream>
#include <memory>
#include <random>
#include <string>
#include <thread>

#include <grpc/grpc.h>
#include <grpcpp/channel.h>
#include <grpcpp/client_context.h>
#include <grpcpp/create_channel.h>
#include <grpcpp/security/credentials.h>

#include <grpcpp/ext/proto_server_reflection_plugin.h>
#include <grpcpp/grpcpp.h>
#include <grpcpp/health_check_service_interface.h>

#ifdef BAZEL_BUILD
#include "examples/protos/helloworld.grpc.pb.h"
#else
#include "Projectprotofile.grpc.pb.h"
#endif

using grpc::Server;
using grpc::ServerBuilder;
using grpc::ServerContext;
using grpc::ServerReader;
using grpc::ServerReaderWriter;
using grpc::ServerWriter;
using grpc::Status;
using GrpcProject::ProjectService;
using GrpcProject::UserAge;
using GrpcProject::TotalAge;
using GrpcProject::UserID;
using GrpcProject::Informations;
using namespace std;

struct People
{
    int user_id;
    string name;
    int age;
};



class ServiceImpl final : public ProjectService::Service
{
    public:

        //Unary RPC
        Status GetInformations(ServerContext* context, const UserID* id, Informations* informations) override
        {
            bool IDAvailable = false;
            
            for(int i = 0; i < p.size(); i++)
            {
                if(id->userid() == p[i]->user_id)
                {
                    informations->mutable_userid()->set_userid(p[i]->user_id);
                    informations->set_name(p[i]->name);
                    informations->mutable_age()->set_age(p[i]->age);
                    IDAvailable = true;
                    break;
                }
            }
            if(IDAvailable) 
            {
                return Status::OK;
            }
            else
            {
                cout << "Id is not available";
                return Status::OK;
            }
        }

        //Server streaming RPC
        Status GetPeopleOfAge(ServerContext* context, const UserAge* userage, ServerWriter<Informations>* writer) override
        {
            for(const People* people : p)
            {
                if(people->age == userage->age())
                {
                    Informations info;
                    info.mutable_userid()->set_userid(people->user_id);
                    info.set_name(people->name);
                    info.mutable_age()->set_age(people->age);
                    writer->Write(*&info);
                }
            }
            return Status::OK;
        }

        //Client streaming RPC
        Status GetSumOfAges(ServerContext* context, ServerReader<UserAge>* reader, TotalAge* writer) override
        {
            int total = 0;
            UserAge userage;
            while(reader->Read(&userage))
            {
                for(const People* people : p)
                {
                    if(people->age == userage.age())
                    {
                        total ++;
                    }
                }
            }
            writer->set_totalages(total);
            return Status::OK;
        }

        //Bidirectional streaming RPC
        Status GetUserAgesOfIdBiggerThanGivenID(ServerContext* context, ServerReaderWriter<UserAge, UserID>* stream) override
        {
            UserID user;
            while (stream->Read(&user)) 
            {
                std::unique_lock<std::mutex> lock(mu_);
                for(const People* people : p)
                {
                    if(people->user_id >= user.userid())
                    {
                        UserAge a;
                        a.set_age(people->age);
                        stream->Write(a);
                    }
                }
            }

            return Status::OK;
        }

        void GenerateVector()
        {
            char a = 'A';
            for (int i = 0; i < 26; i++)
            {
                int age = rand() % (30 - 18) + 18;
                string name(1, a);
                People* b = new People {i, name, age};
                p.push_back(b);
                a = a + 1;
            }
        }

        void DisplayVector()
        {
            cout << "UserID" << "\t" << "Name" << "\t" << "Age" << endl;
            for (int i = 0; i < p.size(); i++)
            {
                cout << p[i]->user_id << "\t" << p[i]->name << "\t" << p[i]->age << endl;
            }
        }
    private:
        //synchroneous
        vector<People*> p;
        std::mutex mu_;
};


void RunServer()
{
    std::string server_address("0.0.0.0:50051");
    ServiceImpl service;
    service.GenerateVector();
    service.DisplayVector();
    grpc::EnableDefaultHealthCheckService(true);
    //grpc::reflection::InitProtoReflectionServerBuilderPlugin();
    ServerBuilder builder;

    builder.AddListeningPort(server_address, grpc::InsecureServerCredentials());
    builder.RegisterService(&service);
    std::unique_ptr<Server> server(builder.BuildAndStart());
    std::cout<< "Server listening on port: " + server_address << std::endl;
    server->Wait();
}

int main(int argc, char** argv)
{
	RunServer();
	return 0;
}