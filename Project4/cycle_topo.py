from mininet.topo import Topo

from mininet.cli import CLI



class cycle_topo(Topo):

	def __init__(self):

		Topo.__init__(self)



		# Add hosts

		h1 = self.addHost("h1")

		h2 = self.addHost("h2")

		h3 = self.addHost("h3")



		# Add switches

		s1 = self.addSwitch("s1")

		s2 = self.addSwitch("s2")

		s3 = self.addSwitch("s3")



		# Add links

		self.addLink(h1, s1)

		self.addLink(h2, s2)

		self.addLink(h3, s3)

		self.addLink(s1, s2)

		self.addLink(s1, s3)

		self.addLink(s2, s3)



topos = {"cycle_topo": cycle_topo}
