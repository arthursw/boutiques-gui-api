import re
import sys
import os


if __name__ == '__main__':

	lib = os.path.join(os.path.dirname(__file__), 'boutiques-python2' if sys.version_info[0] < 3 else 'boutiques-python3')
	sys.path.append(str(lib))

	from boutiques.bosh import bosh
	sys.argv[0] = 'bosh'
	sys.exit(bosh())
