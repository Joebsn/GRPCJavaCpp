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

using grpc::Channel;
using grpc::ClientContext;
using grpc::ClientReader;
using grpc::ClientReaderWriter;
using grpc::ClientWriter;
using grpc::Status;
using GrpcProject::ProjectService;
using GrpcProject::UserAge;
using GrpcProject::TotalAge;
using GrpcProject::UserID;
using GrpcProject::Informations;
using namespace std;


class ClientClass
{
	public:
		ClientClass(std::shared_ptr<Channel> channel) : stub_(ProjectService::NewStub(channel)) {}

		std::string GetInformations(const int userid)
		{
			UserID user;
			user.set_userid(userid);

			Informations info;
			ClientContext context;

			Status status = stub_->GetInformations(&context, user, &info);
			if (status.ok())
			{
				cout << "UserID" << "\t" << "Name" << "\t" << "Age" << endl;
				cout << info.mutable_userid()->userid() << "\t" << info.name() 
					<< "\t" << info.mutable_age()->age() << endl;
				std::cout << "Success";
				return "Success";
			}
			else
			{
				std::cout << status.error_code() << ":" << status.error_message() << std::endl;
				return "RPC failed";
			}
		}

		std::string GetPeopleOfAge(const int userage)
		{
			UserAge user;
			user.set_age(userage);

			Informations info;
			ClientContext context;

			std::unique_ptr<ClientReader<Informations>> reader(stub_->GetPeopleOfAge(&context, user));

			while(reader->Read(&info))
			{
				cout << "UserID" << "\t" << "Name" << "\t" << "Age" << endl;
				cout << info.mutable_userid()->userid() << "\t" << info.name() 
					<< "\t" << info.mutable_age()->age() << endl;
			}

			Status status = reader->Finish();
			if (status.ok())
			{
				std::cout << "Success";
				return "Success";
			}
			else
			{
				std::cout << status.error_code() << ":" << status.error_message() << std::endl;
				return "RPC failed";
			}
		}

		std::string GetSumOfAges()
		{
			UserAge user;
			TotalAge totalage;
			ClientContext context;

			std::unique_ptr<ClientWriter<UserAge>> writer(stub_->GetSumOfAges(&context, &totalage));

			for(int i = 0; i < 3; i++)
			{
				int age = 0;
				cout << "Give me an age: " << endl;
				cin >> age;
				user.set_age(age);
				writer->Write(user);
			}

			writer->WritesDone();
			Status status = writer->Finish();
			cout << "Total:\t" << totalage.totalages() << endl;
			if (status.ok())
			{
				std::cout << "Success";
				return "Success";
			}
			else
			{
				std::cout << status.error_code() << ":" << status.error_message() << std::endl;
				return "RPC failed";
			}
		}

		std::string GetUserAgesOfIdBiggerThanGivenID()
		{
			ClientContext context;
			std::shared_ptr<ClientReaderWriter<UserID, UserAge> > stream(stub_->GetUserAgesOfIdBiggerThanGivenID(&context));
			std::thread writer([stream]() 
			{
				for(int i = 0; i < 2; i++)
				{
					UserID user;
					int id = 0;
					cout << "Give me an ID: " << endl;
					cin >> id;
					user.set_userid(id);
					stream->Write(user);
				}
				stream->WritesDone();
			});

			UserAge userage;
			while (stream->Read(&userage)) 
			{
				std::cout << "Got age " << userage.age() << std::endl;
			}
			writer.join();
			Status status = stream->Finish();
			if (status.ok())
			{
				std::cout << "Success";
				return "Success";
			}
			else
			{
				std::cout << status.error_code() << ":" << status.error_message() << std::endl;
				return "RPC failed";
			}
		}

	private:
		std::unique_ptr<ProjectService::Stub> stub_;
};


int main(int argc, char ** argv)
{
	ClientClass client(grpc::CreateChannel("localhost:50051", grpc::InsecureChannelCredentials()));
	std::string reply = client.GetInformations(5);
	std::cout << "GetInformations: " << endl << reply << std::endl << std::endl;
	reply = client.GetPeopleOfAge(20);
	std::cout << "GetPeopleOfAge: " << endl << reply << std::endl << std::endl;
	reply = client.GetSumOfAges();
	std::cout << "GetPeopleOfAge: " << endl << reply << std::endl << std::endl;
	reply = client.GetUserAgesOfIdBiggerThanGivenID();
	std::cout << "GetUserAgesOfIdBiggerThanGivenID: " << endl << reply << std::endl << std::endl;
	return 0;
}