import sys

from thrift.Thrift import TException
from thrift.protocol import TBinaryProtocol
from thrift.transport import TSocket, TTransport

from python.Rs import RealSenseService

sys.path.append('Rs')



def main():
    transport = TSocket.TSocket('127.0.0.1', 4444)
    transport = TTransport.TBufferedTransport(transport)
    protocol = TBinaryProtocol.TBinaryProtocol(transport)
    client = RealSenseService.Client(protocol)
    print('[Client] Opening connection...')
    transport.open()
    print('[Client] Sending a request...')
    result = client.capture()
    print(result.image.width)
    print('[Client] Closing connection...')
    transport.close()
    return result.image


if __name__ == '__main__':
    try:
        main()
    except TException as tx:
        print('%s' % tx.message)
