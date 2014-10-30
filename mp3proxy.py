import tornado.ioloop
import tornado.web
from tornado import gen
import hashlib
import os

class MainHandler(tornado.web.RequestHandler):

    @gen.coroutine
    def get(self):
        f = yield gen.Task(self.convert, self.get_argument("mp3"))
        data = open(f).read()
        self.add_header('Content-length', len(data))
        self.add_header('Content-type', "audio/ogg")
        self.write(data)
        self.flush()
        self.finish()

    def convert(self, url, callback):
        infile = "/tmp/" + hashlib.sha1(url).hexdigest() + ".mp3"
        outfile = "/tmp/" + hashlib.sha1(url).hexdigest() + ".ogg"
        if not os.path.exists(outfile):
            print "downloading.."
            os.system("wget -O '%s' '%s'" % (infile, url))
            print "converting..."
            os.system("avconv -i %s -acodec libvorbis -q:a 5 %s" % (infile, outfile))
        return callback(outfile)
        
application = tornado.web.Application([
    (r"/", MainHandler),
])

if __name__ == "__main__":
    application.listen(8888)
    tornado.ioloop.IOLoop.instance().start()

